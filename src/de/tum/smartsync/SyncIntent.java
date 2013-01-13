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

/**
 * Contains string constants for intent creation and handling.
 * 
 * @author Daniel
 * 
 */
public class SyncIntent {
	// we use human readable descriptions here, as these strings might appear in
	// log files or error messages
	public static final String UPDATE_RESOURCE = "UPDATE_RESOURCE";
	public static final String UPDATE_ERROR = "UPDATE_ERROR";
	public static final String UPDATE_PROGRESS = "UPDATE_PROGRESS";
	// public static final String UPDATE_TABLE = "UPDATE_TABLE";

	public static final String UPDATE_NOW = "UPDATE_NOW";

	/**
	 * Name of the firing or receiving resource manager as a STRING
	 */
	public static final String EXTRA_NAME = "n";

	/**
	 * Name of the updated resource as a STRING
	 */
	public static final String EXTRA_URI = "u";

	/**
	 * An localized description of the error as a STRING
	 */
	public static final String EXTRA_ERROR = "e";

	/**
	 * Already read bytes as an INTEGER
	 */
	public static final String EXTRA_PROGRESS_BYTES_READ = "pr";

	/**
	 * Total number of bytes of the resource as an INTEGER
	 */
	public static final String EXTRA_PROGRESS_BYTES_TOTAL = "pt";

	/**
	 * Current transmission rate in bytes/s as an INTEGER. Might be -1 if it
	 * cant be computed.
	 */
	public static final String EXTRA_PROGRESS_BYTES_PER_SECOND = "bps";

	/**
	 * Cache method as an INTEGER (see CacheProvider for details)
	 */
	public static final String EXTRA_CACHE_METHOD = "cm";

	/**
	 * Cache name as STRING
	 */
	public static final String EXTRA_CACHE_NAME = "cn";

	/**
	 * Proxy method as an INTEGER (see ResoruceProxy for details)
	 */
	public static final String EXTRA_PROXY_METHOD = "pm";

	/**
	 * Proxy extras as an BYTE ARRAY (see ResoruceProxy for details)
	 */
	public static final String EXTRA_PROXY_EXTRA = "pe";
}
