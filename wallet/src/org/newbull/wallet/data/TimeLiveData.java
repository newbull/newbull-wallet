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

package org.newbull.wallet.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.lifecycle.LiveData;
import org.newbull.wallet.WalletApplication;

import java.util.Date;

/**
 * @author Andreas Schildbach
 */
public class TimeLiveData extends LiveData<Date> {
    private final WalletApplication application;

    public TimeLiveData(final WalletApplication application) {
        this.application = application;
    }

    @Override
    protected void onActive() {
        application.registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        setValue(new Date());
    }

    @Override
    protected void onInactive() {
        application.unregisterReceiver(tickReceiver);
    }

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            setValue(new Date());
        }
    };
}
