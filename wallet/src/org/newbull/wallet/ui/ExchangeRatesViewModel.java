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

package org.newbull.wallet.ui;

import android.app.Application;
import androidx.annotation.MainThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import org.newbull.wallet.WalletApplication;
import org.newbull.wallet.data.WalletBalanceLiveData;
import org.newbull.wallet.exchangerate.ExchangeRateDao;
import org.newbull.wallet.exchangerate.ExchangeRateEntry;
import org.newbull.wallet.exchangerate.ExchangeRatesRepository;

import java.util.List;
import java.util.Locale;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesViewModel extends AndroidViewModel {
    private final WalletApplication application;
    private final ExchangeRateDao exchangeRateDao;
    private final MediatorLiveData<List<ExchangeRateEntry>> exchangeRateLiveData = new MediatorLiveData<>();
    private LiveData<List<ExchangeRateEntry>> underlyingExchangeRateLiveData;
    private WalletBalanceLiveData balance;
    private boolean isConstrained = false;
    public final MutableLiveData<String> selectedExchangeRate = new MutableLiveData<>();
    private Event<String> initialExchangeRate;

    public ExchangeRatesViewModel(final Application application) {
        super(application);
        this.application = (WalletApplication) application;
        this.exchangeRateDao = ExchangeRatesRepository.get(this.application).exchangeRateDao();
        setConstraint(null);
    }

    public LiveData<List<ExchangeRateEntry>> getExchangeRates() {
        return exchangeRateLiveData;
    }

    public void setConstraint(final String constraint) {
        if (underlyingExchangeRateLiveData != null)
            exchangeRateLiveData.removeSource(underlyingExchangeRateLiveData);
        if (constraint != null) {
            underlyingExchangeRateLiveData = exchangeRateDao.findByConstraint(constraint.toLowerCase(Locale.US));
            isConstrained = true;
        } else {
            underlyingExchangeRateLiveData = exchangeRateDao.findAll();
            isConstrained = false;
        }
        exchangeRateLiveData.addSource(underlyingExchangeRateLiveData,
                exchangeRates -> exchangeRateLiveData.setValue(exchangeRates));
    }

    public boolean isConstrained() {
        return isConstrained;
    }

    public WalletBalanceLiveData getBalance() {
        if (balance == null)
            balance = new WalletBalanceLiveData(application);
        return balance;
    }

    @MainThread
    public void setInitialExchangeRate(final String exchangeRateCode) {
        initialExchangeRate = new Event<>(exchangeRateCode);
    }

    @MainThread
    public String getInitialExchangeRate() {
        return initialExchangeRate.getContentIfNotHandled();
    }
}
