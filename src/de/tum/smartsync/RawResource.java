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
package de.tum.smartsync;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.tum.smartsync.resource.RawBigResource;

import android.net.Uri;
import android.util.Log;

/**
 * <p>
 * This resource type is intended to be used for small binary resources. The
 * data is stored in a byte[] array.
 * 
 * <p>
 * For large amounts of data (> 200kB) please use the {@link RawBigResource}
 * class.
 * 
 * @author Daniel
 * 
 */
public class RawResource extends Resource {
	private final String LOG_TAG = "RawResource";

	// init with empty content
	protected byte[] data = new byte[0];

	public RawResource(String path) {
		super(path);
	}

	public RawResource(Uri path) {
		super(path);
	}

	/**
	 * Use this if you want to read from this resource
	 * 
	 * @throws IOException
	 */
	public InputStream getNewInputStream() throws IOException {
		return new ByteArrayInputStream(data);
	}

	/**
	 * Returns the size of the data
	 * 
	 * @return Size in bytes
	 */
	public long getSize() {
		return data.length;
	}

	/**
	 * Stores a copy of the data in the internal array. Creates a warning if you
	 * try to store more than 200kb, as this is negatively affects performance
	 * and battery life
	 * 
	 * @param data
	 *            The data to be stored.
	 */
	public void setData(byte[] data) {
		if (data.length > 1024 * 200)
			Log.w(LOG_TAG,
					"You really should not use a normal RawResource for data which is bigger than 200 KiB.");
		this.data = new byte[data.length];
		System.arraycopy(data, 0, this.data, 0, data.length);
	}

	/**
	 * Returns a reference of the internal data
	 */
	public byte[] getFlatData() {
		return this.data;
	}

}
