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

import android.net.Uri;

/**
 * <p>
 * This class represents a resource that should be kept up to date. It can be
 * configured using the RessourceConfig object.
 * 
 * <p>
 * Please keep in mind that this class is an abstract one. For your application
 * you want to use something like {@link RawResource}, etc.
 * 
 * @author Daniel
 */
public abstract class Resource {

	public static final int NOT_AVAILABLE = -100;
	public static final int UPDATING = 0;
	public static final int QUALITY_WORST = 1;
	public static final int QUALITY_FAIR = 100;
	public static final int QUALITY_GOOD = 200;
	public static final int QUALITY_BEST = 300;

	/**
	 * An Uri path which identifies this resource
	 */
	protected Uri mPath;

	/**
	 * The current status of this resource
	 */
	protected int mStatus = NOT_AVAILABLE;

	/**
	 * Describes the characteristics and requirements regarding this resource.
	 */
	protected ResourceConfig mResourceConfig = new ResourceConfig();

	public Resource(Uri path) {
		if (path.getPath() == null || path.getPath().isEmpty()) {
			throw new IllegalArgumentException(
					"The ressources path must not be empty!");
		} // (T)
		this.mPath = path;
	}

	public Resource(String path) {
		this(buildPath(path));
	}

	protected static Uri buildPath(String path) {
		return new Uri.Builder().encodedPath(path).build();
	}

	public Uri getPathUri() {
		return mPath;
	}

	/**
	 * Returns the current configuration object which can be used for specifying
	 * the objects parameters.
	 * 
	 * @return The current configuration object
	 */
	public ResourceConfig getConfig() {
		return mResourceConfig;
	}

	public void setConfig(ResourceConfig resourceConfig) {
		this.mResourceConfig = resourceConfig;
	}

	public int getStatus() {
		return mStatus;
	}

	public void setStatus(int status) {
		this.mStatus = status;
	}

	/**
	 * Puts the resource in a closed status
	 */
	public void close() {

	}

}
