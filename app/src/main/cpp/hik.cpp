#include <jni.h>
#include <stdio.h>
#include "HCNetSDK.h"

#include <string>
#include <string.h>

extern "C" {

JNIEXPORT jboolean
JNICALL Java_hikvision_zhanyun_com_hikvision_HikDevice_setPTZLimit(JNIEnv *env,
                                                                   jobject /* this */,
                                                                   jint logId,
                                                                   jbyte enable) {
    int iConut = 1;
    NET_DVR_PTZ_LIMITCOND m_struLimitCond = {0};
    NET_DVR_PTZ_LIMITCFG m_struLimitCfg = {0};
    m_struLimitCond.dwSize = sizeof(m_struLimitCond);
    m_struLimitCond.byLimitMode = 1;
    m_struLimitCond.dwChan = 1;

    m_struLimitCfg.dwSize = sizeof(m_struLimitCfg);
    m_struLimitCfg.byEnable = (BYTE) enable;

    DWORD *pStatus = new DWORD[iConut];
    memset(pStatus, 0, sizeof(DWORD) * iConut);
    jboolean isSuccess = (jboolean) NET_DVR_SetDeviceConfig(logId, NET_DVR_SET_LIMITCFG, 1,
                                                            &m_struLimitCond,
                                                            iConut * sizeof(m_struLimitCond),
                                                            pStatus, &m_struLimitCfg,
                                                            iConut * sizeof(m_struLimitCfg));
    return isSuccess;
}

}

