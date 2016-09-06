package com.liferay.mobile.screens.base.thread;

import com.liferay.mobile.screens.base.thread.event.BasicThreadEvent;
import com.liferay.mobile.screens.base.thread.event.ErrorThreadEvent;
import com.liferay.mobile.screens.base.thread.event.OfflineEventNew;
import com.liferay.mobile.screens.base.thread.listener.CacheListener;
import com.liferay.mobile.screens.base.thread.listener.OfflineListenerNew;
import com.liferay.mobile.screens.cache.Cache;
import com.liferay.mobile.screens.cache.OfflinePolicy;
import com.liferay.mobile.screens.cache.executor.Executor;
import com.liferay.mobile.screens.util.EventBusUtil;
import com.liferay.mobile.screens.util.LiferayLogger;
import java.lang.reflect.ParameterizedType;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * @author Javier Gamarra
 */
public abstract class BaseCachedThreadRemoteInteractor<L extends OfflineListenerNew, E extends OfflineEventNew>
	extends BaseThreadInteractor<L, E> {

	protected long groupId;
	protected long userId;
	protected Locale locale;
	protected OfflinePolicy offlinePolicy;
	protected CacheListener cacheListener;

	//TODO use events as interface that way we don't need to cast between varargs
	public void start(final Object... args) {
		Executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					if (offlinePolicy == OfflinePolicy.CACHE_FIRST) {
						try {
							boolean retrievedFromCache = cached(args);

							if (!retrievedFromCache) {
								online(true, null, args);
							}
						} catch (Exception e) {
							online(true, e, args);
						}
					} else if (offlinePolicy == OfflinePolicy.CACHE_ONLY) {
						LiferayLogger.i("Trying to retrieve object from cache");

						boolean retrievedFromCache = cached(args);

						if (!retrievedFromCache) {
							throw new NoSuchElementException();
						}
					} else if (offlinePolicy == OfflinePolicy.REMOTE_FIRST) {
						try {
							online(false, null, args);
						} catch (Exception e) {
							LiferayLogger.e("Retrieve online first failed, trying cached version", e);

							boolean retrievedFromCache = cached(args);

							if (!retrievedFromCache) {
								throw new RuntimeException("Not found in cache", e);
							}
						}
					} else {
						online(false, null, args);
					}
				} catch (Exception e) {
					BasicThreadEvent event = new ErrorThreadEvent(e);
					decorateBaseEvent(event);
					EventBusUtil.post(event);
				}
			}
		});
	}

	@SuppressWarnings("unused")
	public void onEventMainThread(E event) {
		try {
			LiferayLogger.i("event = [" + event + "]");

			if (isInvalidEvent(event)) {
				return;
			}

			if (event.isFailed()) {
				onFailure(event.getException());
			} else {

				if (event.isOnlineRequest()) {
					storeToCache(event);
				}

				onSuccess(event);
			}
		} catch (Exception e) {
			onFailure(e);
		}
	}

	protected void online(boolean triedOffline, Exception e, Object[] args) throws Exception {

		if (triedOffline) {
			LiferayLogger.i("Retrieve from cache first failed, trying online");
		}

		retrievingOnline(triedOffline, e);

		E newEvent = execute(args);
		if (newEvent != null) {
			decorateEvent(newEvent, false);
			newEvent.setCacheKey(getIdFromArgs(args));
			EventBusUtil.post(newEvent);
		}
	}

	protected void decorateEvent(OfflineEventNew event, boolean cachedRequest) {
		decorateBaseEvent(event);
		event.setGroupId(groupId);
		event.setLocale(locale);
		event.setUserId(userId);
		event.setCachedRequest(cachedRequest);
	}

	protected boolean cached(Object... args) throws Exception {

		String cacheKey = getIdFromArgs(args);
		Class aClass = getEventClass();

		E offlineEvent = (E) Cache.getObject(aClass, groupId, userId, locale, cacheKey);

		if (offlineEvent != null) {
			decorateBaseEvent(offlineEvent);
			offlineEvent.setCachedRequest(true);
			EventBusUtil.post(offlineEvent);
			loadingFromCache(true);
			return true;
		}
		loadingFromCache(false);
		return false;
	}

	protected Class getEventClass() {

		Class aClass = (Class) getClass();
		while (!(aClass.getGenericSuperclass() instanceof ParameterizedType)) {
			aClass = aClass.getSuperclass();
		}

		return (Class) ((ParameterizedType) aClass.getGenericSuperclass()).getActualTypeArguments()[1];
	}

	protected void storeToCache(E event) throws Exception {

		storingToCache(event);

		Cache.storeObject(event);
	}

	protected void retrievingOnline(boolean triedOffline, Exception e) {
		if (cacheListener != null) {
			cacheListener.retrievingOnline(triedOffline, e);
		}
	}

	protected void storingToCache(E event) {
		if (cacheListener != null) {
			cacheListener.storingToCache(event);
		}
	}

	protected void loadingFromCache(boolean loadingFromCache) {
		if (cacheListener != null) {
			cacheListener.loadingFromCache(loadingFromCache);
		}
	}

	@Override
	public void onScreenletAttached(L listener) {
		this.listener = listener;
		this.cacheListener = (CacheListener) listener;
		EventBusUtil.register(this);
	}

	@Override
	public void onScreenletDetached(L listener) {
		EventBusUtil.unregister(this);
		this.listener = null;
		this.cacheListener = null;
	}

	protected abstract String getIdFromArgs(Object... args);

	protected OfflinePolicy getOfflinePolicy() {
		return offlinePolicy;
	}

	public void setOfflinePolicy(OfflinePolicy offlinePolicy) {
		this.offlinePolicy = offlinePolicy;
	}

	public void setGroupId(long groupId) {
		this.groupId = groupId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}
}
