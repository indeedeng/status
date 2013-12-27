package com.indeed.status.web;

import com.google.common.base.Function;
import com.indeed.status.core.CheckStatus;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;

/**
 *
 */
public class AbstractResponseWriter {
    public static final Function<CheckStatus, Integer> FN_PUBLIC_RESPONSE =
            new Function<CheckStatus, Integer>() {
                @Override
                public Integer apply (@Nullable CheckStatus status) {
                    final int result;

                    if ( null == status ) {
                        result = SC_ERROR;

                    } else {
                        switch( status ) {
                            case OK:
                            case MINOR:
                            case MAJOR:
                                result = SC_OK;
                                break;
                            case OUTAGE:
                            default:
                                result = SC_ERROR;
                                break;
                        }
                    }

                    return result;
                }
            };
    public static final Function<CheckStatus, Integer> FN_PRIVATE_RESPONSE =
            new Function<CheckStatus, Integer>() {
                @Override
                public Integer apply (@Nullable CheckStatus status) {
                    final int result;

                    if ( null == status ) {
                        result = SC_ERROR;

                    } else {
                        switch( status ) {
                            case OK:
                                result = SC_OK;
                                break;
                            case MINOR:
                            case MAJOR:
                            case OUTAGE:
                            default:
                                result = SC_ERROR;
                                break;
                        }
                    }

                    return result;
                }
            };

    public static final int SC_OK = HttpServletResponse.SC_OK;
    public static final int SC_ERROR = 512;
}
