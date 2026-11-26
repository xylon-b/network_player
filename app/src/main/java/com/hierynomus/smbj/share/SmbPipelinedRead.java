package com.hierynomus.smbj.share;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.mssmb2.messages.SMB2ReadResponse;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * smbj의 비동기 읽기(readAsync)는 패키지 안에서만 쓸 수 있어서, 같은 패키지에 작은 통로를 둔다.
 * 다운로드할 때 읽기 요청을 여러 개 미리 보내 두면(파이프라인) 왕복 대기 시간이 겹쳐서 훨씬 빨라진다.
 */
public final class SmbPipelinedRead {
    private SmbPipelinedRead() {}

    /** 한 번의 읽기 요청에 담을 수 있는 최대 크기(서버와 합의한 값). */
    public static int chunkSize(File file) {
        return file.share.getReadBufferSize();
    }

    public static Future<SMB2ReadResponse> request(File file, long offset, int length) {
        return file.readAsync(offset, length);
    }

    /** 요청 결과를 기다려 데이터를 돌려준다. 파일 끝이면 빈 배열. */
    public static byte[] await(Future<SMB2ReadResponse> future, long timeoutMs) throws Exception {
        SMB2ReadResponse r = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        long status = r.getHeader().getStatusCode();
        if (status == NtStatus.STATUS_END_OF_FILE.getValue()) return new byte[0];
        if (!NtStatus.isSuccess(status)) throw new SMBApiException(r.getHeader(), "Read failed");
        return r.getData();
    }
}
