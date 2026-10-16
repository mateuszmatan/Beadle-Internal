package com.bbh.itss.beadle.internal.application.calllog;

import com.bbh.itss.beadle.internal.application.ReadOnly;
import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.calllog.port.in.CallLogUseCase;
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class CallLogService implements CallLogUseCase {

    private final CallLogPort callLog;

    @Override
    @ReadOnly
    public CallLogPage find(CallLogQuery query) {
        return callLog.find(query);
    }
}
