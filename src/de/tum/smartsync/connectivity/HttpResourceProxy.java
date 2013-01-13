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
package de.tum.smartsync.connectivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.background.UpdateWorker;
import de.tum.smartsync.helper.TimeProvider;

/**
 * This class is used by the update Service in order to establish HTTP
 * connections to authorities.
 * 
 * @author Daniel
 * 
 */
public class HttpResourceProxy extends ResourceProxy {

	private static final String TAG = "HttpResourceProxy";

	public HttpResourceProxy(Uri authority, Context context) {
		if (!authority.getScheme().equalsIgnoreCase("http"))
			throw new IllegalArgumentException(
					"The authority must have the scheme \"http\" but it has the scheme \""
							+ authority.getScheme() + "\"");

		// DO NOT ACTIVATE! The HttpResponseCache is the main reason why the
		// connection has been so slow during testing...
		// enableHttpResponseCache(context);

		disableConnectionReuseIfNecessary();

		this.mAuthority = authority;
	}

	public HttpResourceProxy(byte[] proxyExtra, Context context) {
		this(Uri.parse(internalUnmarshallAuthority(proxyExtra)), context);
	}

	// for HTTPS extending
	protected HttpResourceProxy() {
	}

	@Override
	public void doLoad(RawResource r, List<NameValuePair> params)
			throws IOException, DoNotUpdateException {
		final long start = TimeProvider.currentTimeMillis();

		// extract this for informing the background service later on
		try {
			currentUri = r.getPathUri().toString();
		} catch (Exception ignore) {
		}
		informProgressListener(0, -1, false);

		// build url
		Uri uri = buildUri(r, params);
		URL url = new URL(uri.toString());

		// build up connection
		HttpURLConnection conn = null;
		try {
			Log.d(TAG, "Loading from url: " + url.toString());
			conn = (HttpURLConnection) url.openConnection();
			conn.setIfModifiedSince(getTimestampFromParams(params));

			// parse HTTP response status
			final int respCode = conn.getResponseCode();

			if (respCode == HttpStatus.SC_NOT_MODIFIED)
				throw new DoNotUpdateException(
						DoNotUpdateException.EXC_MESSAGE_NOT_MODIFIED);
			if (respCode != HttpStatus.SC_OK)
				throw new IOException(
						DoNotUpdateException.EXC_MESSAGE_BAD_RESPONSE
								+ conn.getResponseMessage());

			// open input stream to HTTP response
			int contentLen = conn.getContentLength();
			InputStream in = conn.getInputStream();

			// replace resource content (read the inputstream)
			replaceResourceContent(r, in, contentLen);

		} catch (DoNotUpdateException noUpdate) {
			// Log.v(TAG, "Did not update resource (" + uri.toString()
			// + ") because: " + noUpdate.getMessage());
			throw noUpdate;
		} finally {

			if (conn != null)
				conn.disconnect();
		}

		final long dur = TimeProvider.currentTimeMillis() - start;
		Log.d(TAG, "Loaded " + r.getSize() + " bytes in " + dur + "ms.");
	}

	/**
	 * Workaround from
	 * http://android-developers.blogspot.de/2011/09/androids-http-clients.html
	 */
	@SuppressWarnings("deprecation")
	protected void disableConnectionReuseIfNecessary() {
		// HTTP connection reuse which was buggy pre-froyo
		if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) {
			System.setProperty("http.keepAlive", "false");
		}
	}

	/**
	 * Workaround from
	 * http://android-developers.blogspot.de/2011/09/androids-http-clients.html
	 */
	protected void enableHttpResponseCache(Context context) {
		try {
			long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
			File httpCacheDir = new File(context.getCacheDir(), "http");
			Class.forName("android.net.http.HttpResponseCache")
					.getMethod("install", File.class, long.class)
					.invoke(null, httpCacheDir, httpCacheSize);
		} catch (Exception httpResponseCacheNotAvailable) {
			// ignore
		}
	}

	@Override
	public byte[] internalMarshall() {
		// no over-flow as it automatically grows
		ByteBuffer bb = ByteBuffer.allocate(256);
		char[] authority = this.mAuthority.toString().toCharArray();

		bb.putInt(authority.length);
		for (char c : authority)
			bb.putChar(c);

		return bb.array();
	}

	private static String internalUnmarshallAuthority(byte[] data) {
		ByteBuffer bb = ByteBuffer.wrap(data);

		int len = bb.getInt();

		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(bb.getChar());

		return sb.toString();
	}

	protected long getTimestampFromParams(List<NameValuePair> params) {
		for (NameValuePair p : params) {
			if (p.getName().equals(UpdateWorker.HTTP_PARAM_TIMESTAMP))
				return Long.parseLong(p.getValue());
		}
		return 0;
	}

	@Override
	public int getProxyMethod() {
		return ResourceProxy.METHOD_HTTP;
	}
}
