/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.newbull.wallet.ui.preference;

import android.os.Handler;
import android.os.Looper;
import com.google.common.net.HostAndPort;
import org.newbull.wallet.Constants;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author Andreas Schildbach
 */
public abstract class ResolveDnsTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;

    public ResolveDnsTask(final Handler backgroundHandler) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
    }

    public final void resolve(final HostAndPort hostAndPort) {
        backgroundHandler.post(() -> {
            try {
                final InetAddress address = InetAddress.getByName(hostAndPort.getHost()); // blocks on network
                final int port = hostAndPort.getPortOrDefault(Constants.NETWORK_PARAMETERS.getPort());
                final InetSocketAddress socketAddress = new InetSocketAddress(address, port);
                callbackHandler.post(() -> onSuccess(hostAndPort, socketAddress));
            } catch (final UnknownHostException x) {
                callbackHandler.post(() -> onUnknownHost(hostAndPort));
            }
        });
    }

    protected abstract void onSuccess(HostAndPort hostAndPort, InetSocketAddress socketAddress);

    protected abstract void onUnknownHost(HostAndPort hostAndPort);
}
