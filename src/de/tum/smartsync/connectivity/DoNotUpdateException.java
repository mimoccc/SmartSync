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

/**
 * Thrown by a resource proxy if there are valid reasons to not update a
 * resource. Examples are bad response codes or the server telling that the
 * resource has not been updated in the mean time.
 * 
 * @author Daniel
 */
public class DoNotUpdateException extends Exception {

	private static final long serialVersionUID = 6539256127065109226L;

	public static final String EXC_MESSAGE_BAD_RESPONSE = "BAD RESPONSE CODE: Did not get HTTP/OK Code, but ";
	public static final String EXC_MESSAGE_NOT_MODIFIED = "NOT MODIFIED";

	public DoNotUpdateException(String string) {
		super(string);
	}
}
