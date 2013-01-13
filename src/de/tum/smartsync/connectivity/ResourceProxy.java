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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.List;

import org.apache.http.NameValuePair;

import android.content.Context;
import android.net.Uri;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.background.ProgressListener;
import de.tum.smartsync.helper.TimeProvider;
import de.tum.smartsync.resource.RawBigResource;

/**
 * A realization of this abstract resource proxy class implements a source for
 * gathering resources. The methods are usually called by the background update
 * service.
 * 
 * @author Daniel
 * 
 */
public abstract class ResourceProxy {

	@SuppressWarnings("unused")
	private static final String TAG = "ResourceProxy";

	public static final int METHOD_HTTP = 0x1;
	public static final int METHOD_HTTPS = 0x2;
	public static final int METHOD_CONTENT_RESOLVER = 0x3;

	private static final long PROGRESS_UPDATE_INTERVAL = 250; // 125ms

	protected static final int BUFFER_SIZE = 4 * 1024; // 4kB

	protected Uri mAuthority;

	private long noUpdateBefore = 0L;

	// used for calculating transmission speed
	private long lastUpdate = 0L;

	// used for calculating transmission speed
	private long lastByteCount = 0L;

	protected String currentUri = null;

	/**
	 * The current progress listener. Might be <code>null</code> if there's no
	 * such.
	 */
	protected ProgressListener mProgressListener = null;

	/**
	 * <p>
	 * Initiates a request to the authority server and replaces the resources
	 * content.
	 * 
	 * <p>
	 * This method will block until it is finished!
	 * 
	 * @param r
	 *            The resource to be updated. This should be an object created
	 *            by the update service and independent from the UI thread
	 * @param parameters
	 *            Additional parameters which should be transfered to the
	 *            server.
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws DoNotUpdateException
	 *             Thrown when there are reason that the resource should not be
	 *             updated (e.g. not modified)
	 */
	public abstract void doLoad(RawResource r, List<NameValuePair> parameters)
			throws MalformedURLException, IOException, DoNotUpdateException;

	/**
	 * <p>
	 * Replaces the resources content using the provided data from the
	 * InputStream
	 * 
	 * <p>
	 * This method is blocking!
	 * 
	 * @param contentLen
	 *            If the content length is known this should be used to avoid
	 *            unnecessary allocations of byte buffers. Otherwise this should
	 *            be -1.
	 * @param r
	 *            The resource which content is to be replaced
	 * @param in
	 *            The InputStream to be read from
	 */
	protected void replaceResourceContent(RawResource r, InputStream in,
			int contentLen) throws IOException {

		// alternative handling for BigRawResources
		if (r instanceof RawBigResource) {
			this.internalReplaceRawBigResourceContent((RawBigResource) r, in,
					contentLen);
			return;
		}

		ByteArrayOutputStream bos = null;
		if (contentLen >= 0) {
			// Content length known
			bos = new ByteArrayOutputStream(contentLen);
		} else {
			// Content length unknown
			bos = new ByteArrayOutputStream();
		}

		byte[] buf = new byte[BUFFER_SIZE];

		while (true) {
			int len = in.read(buf);

			// finished reading
			if (len == -1)
				break;

			// transport to buffer
			bos.write(buf, 0, len);

			// inform other about progress
			informProgressListener(bos.size(), contentLen, false);
		}
		bos.flush();
		r.setData(bos.toByteArray());

		informProgressListener(bos.size(), contentLen, true);

	}

	/**
	 * Alternativ handling for BigRawResources avoid unneccessary memory
	 * operations.
	 * 
	 * @param contentLen
	 *            If the content length is known this should be used to avoid
	 *            unnecessary allocations of byte buffers. Otherwise this should
	 *            be -1.
	 * @throws IOException
	 */
	private void internalReplaceRawBigResourceContent(RawBigResource r,
			InputStream in, int contentLen) throws IOException {
		OutputStream os = r.getOutputStream();

		byte[] buf = new byte[BUFFER_SIZE];
		int size = 0;

		while (true) {
			int len = in.read(buf);
			size += len;

			// finished reading (E)
			if (len == -1)
				break;

			// transport to buffer
			os.write(buf, 0, len);

			// inform other about progress
			informProgressListener(size, contentLen, false);
		}
		os.flush();

		informProgressListener(size, contentLen, true);
	}

	/**
	 * Builds the Uri for the respective resource considering mAuthority, path
	 * and additional query parameters.
	 * 
	 * @param params
	 *            Might be <code>null</code>.
	 */
	protected Uri buildUri(RawResource r, List<NameValuePair> params) {
		Uri.Builder b = mAuthority.buildUpon();
		b.path(r.getPathUri().toString());

		if (params != null) {
			for (NameValuePair pair : params)
				b.appendQueryParameter(pair.getName(), pair.getValue());
		}

		return b.build();
	}

	public abstract int getProxyMethod();

	/**
	 * Saves the internal configuration of this resource proxy into a byte
	 * array. If this method returns something != null the resource proxy should
	 * also implement an constructor which can handle this marshalled data.
	 * 
	 * @return might be <code>null</code> it no configuration has to be stored.
	 */
	abstract byte[] internalMarshall();

	/**
	 * Saves the internal configuration of this resource proxy into a byte
	 * array. If this method returns something != null the resource proxy should
	 * also implement an constructor which can handle this marshalled data.
	 * 
	 * @return might be <code>null</code> it no configuration has to be stored.
	 */
	public byte[] getProxyExtra() {
		return this.internalMarshall();
	}

	/**
	 * Returns a new instance of a cache provider implementing the given
	 * proxyMethod. The proxyExtra field is used to restore configuration (e.g.
	 * authority information) and can be created by the <code>marshall()</code>
	 * method.
	 * 
	 * @throws IllegalArgumentException
	 *             is thrown if none proxy for the given proxyMethod exists.
	 * 
	 * @return A new instance of a sub class of ResourceProxy.
	 */
	public static ResourceProxy getResourceProxy(Context context,
			int proxyMethod, byte[] proxyExtra) {
		switch (proxyMethod) {
		case METHOD_HTTP:
			return new HttpResourceProxy(proxyExtra, context);
		case METHOD_HTTPS:
			return new HttpsResourceProxy(proxyExtra, context);
		default:
			throw new IllegalArgumentException(
					"Unknown or unimplemented proxy method: " + proxyMethod);
		}
	}

	/**
	 * Returns the authority using the <code>Uri.toString()</code> method.
	 */
	public String getAuthorityAsString() {
		return this.mAuthority.toString();
	}

	/**
	 * Set (or replaces) the progress listener.
	 */
	public void setProgressListener(ProgressListener listener) {
		this.mProgressListener = listener;
	}

	/**
	 * Informs the current listener if any.
	 * 
	 * @param byteCnt
	 *            Number of bytes already loaded
	 * @param total
	 *            Number of bytes in total to read
	 * @param force
	 *            if the listener should be informed regardless of any waiting
	 *            interval
	 */
	protected void informProgressListener(int byteCnt, int total, boolean force) {
		if (mProgressListener == null)
			return;

		// skip if there was not enough time since the last progress update
		final long NOW = TimeProvider.currentTimeMillis();
		if (!force && NOW < noUpdateBefore)
			return;
		noUpdateBefore = NOW + PROGRESS_UPDATE_INTERVAL;

		int bytesPerSecond = -1;
		if (lastUpdate != 0L) {
			final long byteDiff = byteCnt - lastByteCount;
			final long timeDiff = NOW - lastUpdate;

			// 1000, as there are 1000ms per second
			bytesPerSecond = (int) ((1000 * byteDiff) / timeDiff);
		}

		// save for next run
		lastByteCount = byteCnt;
		lastUpdate = NOW;

		mProgressListener
				.onProgress(currentUri, byteCnt, total, bytesPerSecond);
	}
}
