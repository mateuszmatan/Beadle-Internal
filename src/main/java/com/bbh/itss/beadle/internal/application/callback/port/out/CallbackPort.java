package com.bbh.itss.beadle.internal.application.callback.port.out;

import com.bbh.itss.beadle.internal.domain.callback.StatusCallback;

public interface CallbackPort {

    void notify(StatusCallback callback);
}
