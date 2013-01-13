// SmartSync is an Android Framework for Smart Mobile Synchronization
// Copyright (C) 2013 Daniel Hugenroth
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package de.tum.smartsync.background;

import java.io.IOException;
import java.net.MalformedURLException;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;
import de.tum.smartsync.SyncIntent;
import de.tum.smartsync.caching.CacheProvider;
import de.tum.smartsync.connectivity.ConnectionGuru;

/**
 * Abstract class providing the necessary methods for UpdateServices for parsing
 * incoming intents and creating update intents for the {@link ResourceManager}.
 * 
 * @author Daniel
 * 
 */
public abstract class UpdateService extends IntentService implements
		ProgressListener {

	private final static String TAG = "UpdateService";

	public UpdateService(String name) {
		super(name);
	}

	public String mRessourceManagerName;
	public int cacheMethod;
	public String cacheName;
	public int proxyMethod;
	public byte[] proxyExtras;
	public CacheProvider cache;

	@Override
	protected void onHandleIntent(Intent intent) {
		// Debug.startMethodTracing("BACKRGOUND");

		Log.d(TAG, "Received intent: " + intent.toString());
		final String action = intent.getAction();

		// ignore if it is no update now intent
		if (!action.equals(SyncIntent.UPDATE_NOW))
			return;

		// Parse all the intent content
		parseIntentContent(intent);

		Log.d(TAG, "Received an UPDATE_NOW intent from "
				+ mRessourceManagerName + ".");

		cache = CacheProvider.getCache((Context) this, cacheMethod, cacheName);

		// update resources
		try {

			this.internalUpdateResources((Context) this);

		} catch (RuntimeException e) {
			fireErrorBroadcast(e.getLocalizedMessage(), mRessourceManagerName);
			e.printStackTrace();
		} catch (Exception e) {
			fireErrorBroadcast("Severe Error: " + e.getLocalizedMessage(),
					mRessourceManagerName);
			e.printStackTrace();
		}

		cache.close();

		Log.d(TAG, "I will now rest in peace.");

		// Debug.stopMethodTracing();
	}

	/**
	 * Parses all information which are handed over by the Intent sent by the
	 * resource manager
	 */
	private void parseIntentContent(Intent intent) {
		mRessourceManagerName = intent.getStringExtra(SyncIntent.EXTRA_NAME);
		if (mRessourceManagerName == null)
			throw new IllegalArgumentException(
					"Expected EXTRA_NAME but it was not given.");

		cacheMethod = intent.getIntExtra(SyncIntent.EXTRA_CACHE_METHOD, -1);
		if (cacheMethod == -1)
			throw new IllegalArgumentException(
					"Expected EXTRA_CACHE_METHOD but it was not given.");

		cacheName = intent.getStringExtra(SyncIntent.EXTRA_CACHE_NAME);
		if (cacheName == null)
			throw new IllegalArgumentException(
					"Expected EXTRA_CACHE_NAME but it was not given.");

		proxyMethod = intent.getIntExtra(SyncIntent.EXTRA_PROXY_METHOD, -1);
		if (proxyMethod == -1)
			throw new IllegalArgumentException(
					"Expected EXTRA_PROXY_METHOD but it was not given.");

		proxyExtras = intent.getByteArrayExtra(SyncIntent.EXTRA_PROXY_EXTRA);
		if (proxyExtras == null)
			throw new IllegalArgumentException(
					"Expected EXTRA_PROXY_EXTRA but it was not given.");
	}

	/**
	 * This has to be implemented by the concrete UpdateService
	 * 
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	abstract protected void internalUpdateResources(Context context)
			throws MalformedURLException, IOException;

	protected void fireUpdateResource(String uri) {
		Intent bcIntent = new Intent(SyncIntent.UPDATE_RESOURCE);
		bcIntent.putExtra(SyncIntent.EXTRA_NAME, mRessourceManagerName);
		bcIntent.putExtra(SyncIntent.EXTRA_URI, uri);
		sendBroadcast(bcIntent);
	}

	/**
	 * Basic update decision method for very, very basic decisions
	 */
	protected boolean shouldUpdate(CacheProvider cache, RawResource r, int conn) {
		int currentQuality = cache.getCachingStatus(r);
		Log.d(TAG, "Current caching quality: " + currentQuality);

		if (currentQuality == Resource.NOT_AVAILABLE) {
			// there is no local copy, so update
			return true;
		} else if (cache.isOutdated(r)) {
			// is outdated
			return true;
		} else if (currentQuality == Resource.QUALITY_BEST) {
			// the local copy is already in best quality, so do not update
			return false;
		} else {
			if (!r.getConfig().isConnectionDecisive())
				return false;
			// update if we expect to have a better quality
			int expectedQuality = ConnectionGuru.getExpectedQuality(conn);
			if (expectedQuality > currentQuality)
				return true;
			else
				return false;
		}
	}

	@Override
	public void onError(String uri, String message) {
		fireErrorBroadcast(message, uri);
	}

	protected void fireErrorBroadcast(String errorMessage, String uri) {
		Log.v(TAG, "Firing error broadcast: " + errorMessage + "@" + uri);
		Intent bcIntent = new Intent(SyncIntent.UPDATE_ERROR);
		bcIntent.putExtra(SyncIntent.EXTRA_NAME, mRessourceManagerName);
		bcIntent.putExtra(SyncIntent.EXTRA_URI, uri);
		bcIntent.putExtra(SyncIntent.EXTRA_ERROR, errorMessage);
		sendBroadcast(bcIntent);
	}

	/**
	 * Called by the background resource proxy.
	 */
	@Override
	public void onProgress(String currentUri, int bytesCnt, int bytesTotal,
			int bytesPerSecond) {
		Intent bcIntent = new Intent(SyncIntent.UPDATE_PROGRESS);
		bcIntent.putExtra(SyncIntent.EXTRA_NAME, mRessourceManagerName);
		if (currentUri != null)
			bcIntent.putExtra(SyncIntent.EXTRA_URI, currentUri);
		bcIntent.putExtra(SyncIntent.EXTRA_PROGRESS_BYTES_READ, bytesCnt);
		bcIntent.putExtra(SyncIntent.EXTRA_PROGRESS_BYTES_TOTAL, bytesTotal);
		bcIntent.putExtra(SyncIntent.EXTRA_PROGRESS_BYTES_PER_SECOND,
				bytesPerSecond);
		sendBroadcast(bcIntent);

	}

	@Override
	public void updatedResource(String uri) {
		this.fireUpdateResource(uri);
	}
}
