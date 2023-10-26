package com.davfx.ninio.proxy;

import com.davfx.ninio.core.*;

public interface ProxyProvider extends Disconnectable {
	RawSocket.Builder raw();
	WithHeaderSocketBuilder factory();
}
