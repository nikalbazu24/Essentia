package com.hatkid.mkxpz;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/** Supplies Android's trusted roots to the engine's OpenSSL client, locally. */
public final class NetworkTrust {
    private NetworkTrust(){}
    public static File prepare(Context c)throws Exception{
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore)null);
        StringBuilder pem=new StringBuilder();
        for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager){
            for(X509Certificate cert:((X509TrustManager)manager).getAcceptedIssuers()){
                pem.append("-----BEGIN CERTIFICATE-----\n");
                String encoded=Base64.encodeToString(cert.getEncoded(),Base64.NO_WRAP);
                for(int offset=0;offset<encoded.length();offset+=64)
                    pem.append(encoded,offset,Math.min(offset+64,encoded.length())).append('\n');
                pem.append("-----END CERTIFICATE-----\n");
            }
        }
        if(pem.length()==0)throw new IOException("Android no entregó certificados de confianza");
        File dest=new File(c.getCacheDir(),"android-trust.pem"),temp=new File(c.getCacheDir(),"android-trust-"+java.util.UUID.randomUUID()+".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(pem.toString().getBytes("US-ASCII"));out.getFD().sync();}
            if(!temp.renameTo(dest))throw new IOException("No se pudo preparar la conexión segura");
            return dest;
        }finally{temp.delete();}
    }
}
