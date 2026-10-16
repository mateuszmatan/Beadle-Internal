package com.bbh.itss.beadle.internal.application.calllog.port.in;

import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;

public interface CallLogUseCase {

    CallLogPage find(CallLogQuery query);
}
