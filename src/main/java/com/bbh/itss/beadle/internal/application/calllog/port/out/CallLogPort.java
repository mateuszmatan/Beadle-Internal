package com.bbh.itss.beadle.internal.application.calllog.port.out;

import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;

public interface CallLogPort {

    CallLogEntry record(CallLogEntry entry);

    CallLogPage find(CallLogQuery query);
}
