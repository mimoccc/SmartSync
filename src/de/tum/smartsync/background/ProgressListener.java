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

/**
 * Implemented by the {@link UpdateNowService} and called by the resource proxy
 * or the {@link UpdateWorker} implementations.
 * 
 * @author Daniel
 * 
 */
public interface ProgressListener {

	/**
	 * Called by the resource proxy with information about the current download
	 * status.
	 * 
	 * @param currentUri
	 *            The current URI being processed
	 * @param byteCnt
	 *            Number of bytes already loaded
	 * @param progress
	 *            Number of bytes in total to read
	 * @param bytesPerSecond
	 *            Current transmission rate in bytes/s. Might be -1 if it cant
	 *            be computed.
	 */
	public void onProgress(String currentUri, int byteCnt, int progress,
			int bytesPerSecond);

	/**
	 * Called when a resource if finally updated and ready to be retrieved by
	 * the on-lying application.
	 * 
	 * @param uri
	 *            The URI of the resource which has been processed
	 */
	public void updatedResource(String uri);

	/**
	 * Called when the update caused any severe error that could not be handled
	 * 
	 * @param uri
	 *            The URI of the resource which has been processed
	 * @param message
	 *            A human readable error message
	 */
	public void onError(String uri, String message);
}
