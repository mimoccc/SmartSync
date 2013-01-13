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
package de.tum.smartsync.caching;

import android.content.Context;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;

/**
 * <p>
 * The caching provider offers methods for using caching services. The actual
 * realization and strategy lies within the responsibility of the implementing
 * class.
 * 
 * @author Daniel
 * 
 */
public abstract class CacheProvider {

	public static final int METHOD_SQL = 0x1;
	public static final int METHOD_FILE = 0x2;

	/**
	 * <p>
	 * This method should be called when the cache is not longer needed or the
	 * application exits.
	 */
	public abstract void close();

	/**
	 * <p>
	 * Removes all cached resources.
	 */
	public abstract void clearCache();

	/**
	 * <p>
	 * This method is called when the smartphone is currently able to perform
	 * longer background task without major disadvantages. (usually it is
	 * connected to power and not in use)
	 */
	public abstract void doExtensiveWork();

	/**
	 * <p>
	 * Removes the given resource from the cache. This method must be
	 * implemented by the caching provider.
	 * 
	 * @param r
	 *            The resource to be removed
	 */
	public abstract void remove(Resource r);

	/**
	 * <p>
	 * 
	 * This method propagates any changes of a resource to the caching provider.
	 * It is the caching providers decision whether it takes any action
	 * 
	 * @param r
	 *            The resource to be considered
	 */
	public abstract void cache(RawResource r);

	/**
	 * <p>
	 * Returns whether the resource is cached and its current caching status
	 * (including quality, see Resource.QUALITY_*). Only caching results within
	 * the caching limits specified by the resource's configuration are taking
	 * into consideration.
	 * 
	 * @return >0 if the resource is cached. <0 otherwise
	 */
	public abstract int getCachingStatus(Resource r);

	/**
	 * <p>
	 * Fills the resource using the best quality available which lies within the
	 * resources caching limits. This methods might override the current content
	 * of the resource.
	 * 
	 * @param r
	 *            The resource to be filled
	 */
	public abstract void fillResource(RawResource r);

	/**
	 * Returns the implemented cache method of this cache instance.
	 * 
	 * @return See <code>CacheProvider.METHOD_*</code> for valid values.
	 */
	public abstract int getCacheMethod();

	/**
	 * <p>
	 * Gets a cache instance for the given cache method and cache name.
	 */
	public static CacheProvider getCache(Context context, int cacheMethod,
			String name) {
		switch (cacheMethod) {
		case METHOD_SQL:
			return new SqlCacheProvider(name, context);
		case METHOD_FILE:
			return new FileCacheProvider(name, context);
		default:
			throw new IllegalArgumentException(
					"Unknown or unimplemented cache method: " + cacheMethod);
		}
	}

	/**
	 * Returns true if the resources age is greater than its update interval
	 */
	public abstract boolean isOutdated(Resource r);

	public abstract String getCacheName();

	/**
	 * Returns the timestamp (denotes when a resource becomes invalid) of the
	 * best candidate.
	 * 
	 * @return time in seconds (UNIX epoch) or 0 if no fitting resource was
	 *         found.
	 */
	public abstract long getTimestamp(Resource r);

}
