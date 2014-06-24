/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.plugins.local;

import org.syncany.plugins.Plugin;
import org.syncany.plugins.transfer.TransferSettings;
import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.TransferPlugin;

/**
 * Identifies the local storage {@link Plugin} for Syncany.
 * 
 * <p>This plugin can be used for testing or to point to a repository
 * on a mounted remote device or network storage such as an NFS or a 
 * Samba/NetBIOS share.
 * 
 * <p>The class implements defines the identifier, name and 
 * version of the plugin. It furthermore allows the instantiation 
 * of a plugin-specific {@link LocalConnection}. 
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class LocalPlugin extends TransferPlugin {
	public LocalPlugin() {
		super("local");
	}
	
	protected LocalPlugin(String pluginId) {
		super(pluginId);
	}

    @Override
    public TransferSettings createSettings() {
        return new LocalConnection();
    }
    
	@Override
	public TransferManager createTransferManager(TransferSettings connection) {
		return new LocalTransferManager((LocalConnection) connection);
	}
}
