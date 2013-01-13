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

import android.content.BroadcastReceiver;
import de.tum.smartsync.manager.ResourceManager;

/**
 * <p>
 * SyncListener are called by the ResourceManager whenever a resource content
 * changed, a error occurred or the download progress changed.
 * 
 * <p>
 * They will be called from the thread and context within which the
 * {@link ResourceManager} has been created and registered its
 * {@link BroadcastReceiver}.
 * 
 * @author Daniel
 * 
 */
public interface SyncListener {

	/**
	 * Called when a resource's content has been updated.
	 * 
	 * @param r
	 *            The resource that has been updated
	 * @param rm
	 *            The resourceManager informing about this even.
	 */
	public void onUpdate(Resource r, ResourceManager rm);

	/**
	 * Called when an error occured.
	 * 
	 * @param r
	 *            The resource that has caused the error. <code>null</code> if
	 *            the error can not be assigned to a single resource.
	 * @param rm
	 *            The resourceManager informing about this even.
	 * @param error
	 *            A (hopefully) localized message explaining the error.
	 */
	public void onError(String error, Resource r, ResourceManager rm);

	/**
	 * Called a download has made any interesting progess.
	 * 
	 * @param byteCnt
	 *            Number of bytes already loaded
	 * @param bytesTotal
	 *            Number of bytes in total to read
	 * @param bytesPerSecond
	 *            Current transmission rate in byte/s. Might be -1 if it cant be
	 *            computed.
	 * @param r
	 *            The resource which is currently looked at.
	 * @param rm
	 *            The resourceManager informing about this even.
	 */
	public void onProgress(int bytesRead, int bytesTotal, int bytesPerSecond,
			Resource r, ResourceManager rm);
}
