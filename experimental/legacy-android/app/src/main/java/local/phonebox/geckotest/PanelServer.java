package local.phonebox.geckotest;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Restricted loopback bridge. The PC token is never sent to page JavaScript. */
final class PanelServer implements Closeable {
    private static final int LIMIT = 4 * 1024 * 1024;
    private final Context context;
    private final ServerSocket server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final String capability, origin;
    private volatile boolean closed;
    private JSONObject pairedPc;
    private SSLSocketFactory pinnedFactory;

    PanelServer(Context context) throws Exception {
        this.context = context;
        byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
        StringBuilder b = new StringBuilder(); for (byte x : random) b.append(String.format(Locale.US, "%02x", x & 255)); capability = b.toString();
        server = new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        origin = "http://127.0.0.1:" + server.getLocalPort();
        Thread accept = new Thread(() -> { while (!closed) try { final Socket s = server.accept(); workers.execute(() -> handle(s)); } catch (IOException e) { if (!closed) android.util.Log.e("PhoneBoxLegacy", "Accept failed", e); } });
        accept.setDaemon(true); accept.start();
    }
    String url() { return origin + "/" + capability + "/www/index.html"; }
    public void close() { closed = true; try { server.close(); } catch (IOException ignored) {} workers.shutdownNow(); }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(12000); InputStream in=socket.getInputStream(); OutputStream out=socket.getOutputStream();
            String line=line(in); if (line==null) return; String[] request=line.split(" ");
            if (request.length!=3) { respond(out,400,"text/plain",bytes("Bad request")); return; }
            String method=request[0], target=request[1];
            if (!method.equals("GET") && !method.equals("POST")) { respond(out,405,"text/plain",bytes("Method not allowed")); return; }
            Map<String,String> headers=new HashMap<>(); int size=line.length();
            while ((line=line(in))!=null && !line.isEmpty()) { size+=line.length(); if(size>16384) throw new IOException("Headers too large"); int colon=line.indexOf(':'); if(colon>0) headers.put(line.substring(0,colon).trim().toLowerCase(Locale.US),line.substring(colon+1).trim()); }
            String host=headers.get("host"), sentOrigin=headers.get("origin");
            if (!("127.0.0.1:"+server.getLocalPort()).equals(host) || (sentOrigin!=null && !origin.equals(sentOrigin))) { respond(out,403,"text/plain",bytes("Forbidden")); return; }
            String prefix="/"+capability+"/"; if(!target.startsWith(prefix)) { respond(out,404,"text/plain",bytes("Not found")); return; }
            String path=target.substring(prefix.length());
            if(path.startsWith("www/") && method.equals("GET")) { asset(out,path.substring(4)); return; }
            if(!path.startsWith("agent/api/")) { respond(out,404,"text/plain",bytes("Not found")); return; }
            String agentPath=path.substring(5), clean=agentPath.split("\\?",2)[0];
            if(clean.equals("/api/controls/listen") || clean.equals("/api/stream")) { respond(out,503,"text/plain",bytes("Streaming unavailable")); return; }
            boolean image=method.equals("GET") && Arrays.asList("/api/art","/api/procs/icon","/api/controls/radio-logo","/api/apps/icon","/api/discord/img","/api/shortcuts/icon").contains(clean);
            if(!image && !"1".equals(headers.get("x-phonebox-panel"))) { respond(out,403,"text/plain",bytes("Forbidden")); return; }
            if(method.equals("POST") && !origin.equals(sentOrigin)) { respond(out,403,"text/plain",bytes("Forbidden")); return; }
            if(headers.containsKey("transfer-encoding")) { respond(out,400,"text/plain",bytes("Unsupported body")); return; }
            int length=headers.containsKey("content-length")?Integer.parseInt(headers.get("content-length")):0;
            if(length<0 || length>1024*1024) throw new IOException("Body too large"); byte[] body=new byte[length];
            for(int n=0;n<length;) { int count=in.read(body,n,length-n); if(count<0) throw new EOFException(); n+=count; }
            proxy(out,method,agentPath,body,headers.get("content-type"));
        } catch (Exception e) { android.util.Log.w("PhoneBoxLegacy", "Local request failed: "+e.getClass().getSimpleName()); }
        finally { try { socket.close(); } catch (IOException ignored) {} }
    }
    private void asset(OutputStream out,String target) throws Exception {
        String name=target.split("\\?",2)[0];
        if(name.isEmpty() || name.contains("..") || name.contains("%") || name.contains("\\")) { respond(out,403,"text/plain",bytes("Forbidden")); return; }
        byte[] data; try { data=read(context.getAssets().open("www/"+name),LIMIT); } catch(IOException e) { respond(out,404,"text/plain",bytes("Not found")); return; }
        if(name.equals("main.js")) data=bytes(new String(data,"UTF-8").replace("export const API = '/agent/api';","export const API = '/"+capability+"/agent/api';"));
        String mime=name.endsWith(".js")?"text/javascript; charset=utf-8":name.endsWith(".css")?"text/css; charset=utf-8":name.endsWith(".html")?"text/html; charset=utf-8":"application/octet-stream";
        respond(out,200,mime,data);
    }
    /** One pinned TLS factory per pairing: this lets Android reuse existing HTTPS connections. */
    private synchronized void preparePairing() throws Exception {
        if (pairedPc != null && pinnedFactory != null) return;
        JSONObject pair=new JSONObject(new String(read(context.openFileInput("pair.json"),8192),"UTF-8"));
        final String pin=pair.getString("fingerprint").toLowerCase(Locale.US); if(!pin.matches("[0-9a-f]{64}")) throw new CertificateException("Invalid pin");
        TrustManager[] managers={new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Not supported");}
            public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{
                try { byte[] want=new byte[32]; for(int i=0;i<32;i++) want[i]=(byte)Integer.parseInt(pin.substring(i*2,i*2+2),16); if(c==null||c.length==0||!MessageDigest.isEqual(want,MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded()))) throw new CertificateException("Pin mismatch"); }
                catch(CertificateException e){throw e;} catch(Exception e){throw new CertificateException(e);}
            }
        }};
        SSLContext ssl=SSLContext.getInstance("TLSv1.2"); ssl.init(null,managers,new SecureRandom());
        pinnedFactory=ssl.getSocketFactory(); pairedPc=pair;
    }
    private void proxy(OutputStream out,String method,String path,byte[] body,String type) throws Exception {
        try { preparePairing(); } catch(Exception e) { respond(out,503,"text/plain",bytes("Not paired")); return; }
        JSONObject pair=pairedPc;
        HttpsURLConnection c=(HttpsURLConnection)new URL("https",pair.getJSONArray("hosts").getString(0),pair.getInt("port"),path).openConnection();
        c.setSSLSocketFactory(pinnedFactory); c.setHostnameVerifier((h,s)->true); c.setInstanceFollowRedirects(false); c.setConnectTimeout(4000); c.setReadTimeout(10000); c.setRequestMethod(method); c.setRequestProperty("Authorization","Bearer "+pair.getString("token")); c.setRequestProperty("X-PhoneBox-Model","Huawei Gecko prototype"); c.setUseCaches(false);
        boolean reusable=false;
        try {
            if(method.equals("POST")){c.setDoOutput(true); if(type!=null&&!type.contains("\n")&&!type.contains("\r"))c.setRequestProperty("Content-Type",type); c.setFixedLengthStreamingMode(body.length); OutputStream remote=c.getOutputStream(); remote.write(body); remote.close();}
            int code=c.getResponseCode(); if(code<200||code>=600||(code>=300&&code<400)){respond(out,502,"text/plain",bytes("Unexpected response"));return;}
            InputStream remote=code>=400?c.getErrorStream():c.getInputStream(); byte[] result=remote==null?new byte[0]:read(remote,LIMIT); String content=c.getContentType(); reusable=remote!=null; respond(out,code,content==null?"application/octet-stream":content,result);
        } catch(IOException e){respond(out,502,"text/plain",bytes("Agent request failed"));} finally{if(!reusable)c.disconnect();}
    }
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1&&c!='\n'){if(out.size()>8192)throw new IOException("Line too long");if(c!='\r')out.write(c);}return c==-1&&out.size()==0?null:out.toString("UTF-8");}
    private static byte[] read(InputStream in,int limit)throws IOException{try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Too large");out.write(buf,0,n);}return out.toByteArray();}finally{in.close();}}
    private static byte[] bytes(String s)throws UnsupportedEncodingException{return s.getBytes("UTF-8");}
    private static void respond(OutputStream out,int code,String type,byte[] body)throws IOException{if(type.indexOf('\n')>=0||type.indexOf('\r')>=0)type="application/octet-stream";String h="HTTP/1.1 "+code+" Response\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";out.write(h.getBytes("UTF-8"));out.write(body);out.flush();}
}
