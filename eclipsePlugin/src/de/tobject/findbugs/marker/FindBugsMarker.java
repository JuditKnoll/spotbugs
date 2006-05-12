/* 
 * FindBugs Eclipse Plug-in.
 * Copyright (C) 2003 - 2004, Peter Friese
 * Copyright (C) 2005, University of Maryland
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
 
package de.tobject.findbugs.marker;

/**
 * Marker for the findbugs plug-in.
 * 
 * @author Peter Friese
 * @version 1.0
 * @since 13.08.2003
 */
public class FindBugsMarker {
	/**
	 * Marker type for FindBugs warnings.
	 * (should be the plugin id concatenated with ".findbugsMarker")
	 */
	public static final String NAME = "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarker";
	
	/**
	 * Marker attribute recording the bug type.
	 */
	public static final String BUG_TYPE = "BUGTYPE";
	
	/**
	 * Marker attribute recording the unique id of the BugInstance
	 * in its BugCollection.
	 */
	public static final String UNIQUE_ID = "FINDBUGS_UNIQUE_ID";
}
