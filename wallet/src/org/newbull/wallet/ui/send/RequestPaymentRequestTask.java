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

package org.newbull.wallet.ui.send;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import org.newbull.wallet.Constants;
import org.newbull.wallet.R;
import org.newbull.wallet.data.PaymentIntent;
import org.newbull.wallet.ui.InputParser;
import org.newbull.wallet.util.Bluetooth;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Andreas Schildbach
 */
public abstract class RequestPaymentRequestTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;
    private final ResultCallback resultCallback;

    private static final Logger log = LoggerFactory.getLogger(RequestPaymentRequestTask.class);

    public interface ResultCallback {
        void onPaymentIntent(PaymentIntent paymentIntent);

        void onFail(int messageResId, Object... messageArgs);
    }

    public RequestPaymentRequestTask(final Handler backgroundHandler, final ResultCallback resultCallback) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
        this.resultCallback = resultCallback;
    }

    public final static class HttpRequestTask extends RequestPaymentRequestTask {
        @Nullable
        private final String userAgent;

        public HttpRequestTask(final Handler backgroundHandler, final ResultCallback resultCallback,
                @Nullable final String userAgent) {
            super(backgroundHandler, resultCallback);

            this.userAgent = userAgent;
        }

        @Override
        public void requestPaymentRequest(final String url) {
            super.backgroundHandler.post(() -> {
                log.info("trying to request payment request from {}", url);

                final Request.Builder request = new Request.Builder();
                request.url(url);
                request.cacheControl(new CacheControl.Builder().noCache().build());
                final Headers.Builder headers = new Headers.Builder();
                headers.add("Accept", PaymentProtocol.MIMETYPE_PAYMENTREQUEST);
                if (userAgent != null)
                    headers.add("User-Agent", userAgent);
                request.headers(headers.build());

                final Call call = Constants.HTTP_CLIENT.newCall(request.build());
                try {
                    final Response response = call.execute();
                    if (response.isSuccessful()) {
                        final String contentType = response.header("Content-Type");
                        final InputStream is = response.body().byteStream();
                        new InputParser.StreamInputParser(contentType, is) {
                            @Override
                            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                                log.info("received {} via http", paymentIntent);

                                onPaymentIntent(paymentIntent);
                            }

                            @Override
                            protected void error(final int messageResId, final Object... messageArgs) {
                                onFail(messageResId, messageArgs);
                            }
                        }.parse();
                        is.close();
                    } else {
                        final int responseCode = response.code();
                        final String responseMessage = response.message();

                        log.info("got http error {}: {}", responseCode, responseMessage);
                        onFail(R.string.error_http, responseCode, responseMessage);
                    }
                } catch (final IOException x) {
                    log.info("problem sending", x);

                    onFail(R.string.error_io, x.getMessage());
                }
            });
        }
    }

    public final static class BluetoothRequestTask extends RequestPaymentRequestTask {
        private final BluetoothAdapter bluetoothAdapter;

        public BluetoothRequestTask(final Handler backgroundHandler, final ResultCallback resultCallback,
                final BluetoothAdapter bluetoothAdapter) {
            super(backgroundHandler, resultCallback);

            this.bluetoothAdapter = bluetoothAdapter;
        }

        @Override
        public void requestPaymentRequest(final String url) {
            super.backgroundHandler.post(() -> {
                log.info("trying to request payment request from {}", url);

                final BluetoothDevice device = bluetoothAdapter
                        .getRemoteDevice(Bluetooth.decompressMac(Bluetooth.getBluetoothMac(url)));

                try (final BluetoothSocket socket = device
                        .createInsecureRfcommSocketToServiceRecord(Bluetooth.PAYMENT_REQUESTS_UUID);
                        final OutputStream os = socket.getOutputStream();
                        final InputStream is = socket.getInputStream()) {
                    socket.connect();

                    log.info("connected to {}", url);

                    final CodedInputStream cis = CodedInputStream.newInstance(is);
                    final CodedOutputStream cos = CodedOutputStream.newInstance(os);

                    cos.writeInt32NoTag(0);
                    cos.writeStringNoTag(Bluetooth.getBluetoothQuery(url));
                    cos.flush();

                    final int responseCode = cis.readInt32();

                    if (responseCode == 200) {
                        new InputParser.BinaryInputParser(PaymentProtocol.MIMETYPE_PAYMENTREQUEST,
                                cis.readBytes().toByteArray()) {
                            @Override
                            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                                log.info("received {} via bluetooth", paymentIntent);

                                onPaymentIntent(paymentIntent);
                            }

                            @Override
                            protected void error(final int messageResId, final Object... messageArgs) {
                                onFail(messageResId, messageArgs);
                            }
                        }.parse();
                    } else {
                        log.info("got bluetooth error {}", responseCode);

                        onFail(R.string.error_bluetooth, responseCode);
                    }
                } catch (final IOException x) {
                    log.info("problem sending", x);

                    onFail(R.string.error_io, x.getMessage());
                }
            });
        }
    }

    public abstract void requestPaymentRequest(String url);

    protected void onPaymentIntent(final PaymentIntent paymentIntent) {
        callbackHandler.post(() -> resultCallback.onPaymentIntent(paymentIntent));
    }

    protected void onFail(final int messageResId, final Object... messageArgs) {
        callbackHandler.post(() -> resultCallback.onFail(messageResId, messageArgs));
    }
}
