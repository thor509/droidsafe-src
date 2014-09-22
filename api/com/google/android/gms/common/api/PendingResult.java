package com.google.android.gms.common.api;

import droidsafe.annotations.*;
import droidsafe.runtime.*;
import droidsafe.helpers.*;
import java.util.concurrent.TimeUnit;

public abstract interface PendingResult
{

    public abstract com.google.android.gms.common.api.Result await();

    public abstract com.google.android.gms.common.api.Result await(long  l0, java.util.concurrent.TimeUnit  r1);

    public abstract void cancel();

    public abstract boolean isCanceled();

    public abstract void setResultCallback(com.google.android.gms.common.api.ResultCallback  r0);

    public abstract void setResultCallback(com.google.android.gms.common.api.ResultCallback  r0, long  l1, java.util.concurrent.TimeUnit  r2);

    public abstract void a(com.google.android.gms.common.api.PendingResult$a  r0);
}
