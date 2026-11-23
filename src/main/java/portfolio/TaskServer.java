package portfolio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDK-only portfolio task tracker. Localhost-bound, demonstrational authentication-free app. */
public final class TaskServer {
    private static final class Task {
        final int id; final String title; boolean done;
        Task(int id,String title,boolean done){this.id=id;this.title=title;this.done=done;}
    }
    private static final class Store {
        final Path path;
        final List<Task> tasks=new ArrayList<>();
        int nextId=1;
        Store(Path path) throws IOException {this.path=path;load();}
        synchronized void load() throws IOException {
            if (!Files.isRegularFile(path)) return;
            for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)) {
                if(line.isBlank())continue;
                String[] f=line.split("\\t",3);
                if(f.length!=3)throw new IOException("Malformed task database");
                int id=Integer.parseInt(f[0]);
                String title=new String(Base64.getUrlDecoder().decode(f[2]),StandardCharsets.UTF_8);
                tasks.add(new Task(id,title,"1".equals(f[1])));
                nextId=Math.max(nextId,id+1);
            }
        }
        synchronized void save() throws IOException {
            Files.createDirectories(path.toAbsolutePath().getParent());
            StringBuilder out=new StringBuilder();
            for(Task t:tasks) {
                out.append(t.id).append('\t').append(t.done?"1":"0").append('\t')
                    .append(Base64.getUrlEncoder().encodeToString(t.title.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
            }
            Path tmp=Files.createTempFile(path.toAbsolutePath().getParent(),"tasks-",".tmp");
            Files.writeString(tmp,out.toString(),StandardCharsets.UTF_8);
            try {Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }
        synchronized String listJson() {
            StringBuilder out=new StringBuilder("[");
            for(Task t:tasks){
                if(out.length()>1)out.append(',');
                out.append("{\"id\":").append(t.id).append(",\"title\":\"")
                    .append(jsonEscape(t.title)).append("\",\"done\":").append(t.done).append('}');
            }
            return out.append(']').toString();
        }
        synchronized String add(String title) throws IOException {
            String name=title.strip();
            if(name.isEmpty()||name.length()>120)throw new IllegalArgumentException("Title must contain 1-120 characters");
            tasks.add(new Task(nextId++,name,false));save();
            return listJson();
        }
        synchronized String toggle(int id) throws IOException {
            for(Task t:tasks)if(t.id==id){t.done=!t.done;save();return listJson();}
            throw new IllegalArgumentException("Unknown task ID");
        }
    }
    private static String jsonEscape(String input){
        return input.replace("\\","\\\\").replace("\"","\\\"")
                    .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
    private static void send(HttpExchange ex,int code,String type,String body) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        ex.sendResponseHeaders(code,bytes.length);
        try(var out=ex.getResponseBody()){out.write(bytes);}
    }
    private static Map<String,String> params(HttpExchange ex) throws IOException {
        byte[] body=ex.getRequestBody().readNBytes(8193);
        if(body.length>8192)throw new IllegalArgumentException("Request too large");
        Map<String,String> parsed=new LinkedHashMap<>();
        for(String pair:new String(body,StandardCharsets.UTF_8).split("&")){
            if(pair.isBlank())continue;
            String[] parts=pair.split("=",2);
            parsed.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),
                       URLDecoder.decode(parts.length==2?parts[1]:"",StandardCharsets.UTF_8));
        }
        return parsed;
    }
    public static void main(String[] args) throws Exception {
        int port=8080;Path data=Path.of("out","tasks.tsv");
        for(int i=0;i<args.length;i++){
            if(args[i].equals("--port"))port=Integer.parseInt(args[++i]);
            else if(args[i].equals("--data"))data=Path.of(args[++i]);
            else throw new IllegalArgumentException("Unknown argument: "+args[i]);
        }
        Store store=new Store(data);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/",ex->{
            try {
                String path=ex.getRequestURI().getPath(),method=ex.getRequestMethod();
                if(path.equals("/api/health")&&method.equals("GET")){send(ex,200,"application/json","{\"ok\":true}");return;}
                if(path.equals("/api/tasks")&&method.equals("GET")){send(ex,200,"application/json",store.listJson());return;}
                if(path.equals("/api/tasks")&&method.equals("POST")){
                    send(ex,201,"application/json",store.add(params(ex).getOrDefault("title","")));return;
                }
                if(path.matches("/api/tasks/[0-9]+/toggle")&&method.equals("POST")){
                    int id=Integer.parseInt(path.split("/")[3]);
                    send(ex,200,"application/json",store.toggle(id));return;
                }
                if(path.equals("/")&&method.equals("GET")){
                    send(ex,200,"text/html",Files.readString(Path.of("web","index.html")));return;
                }
                if(path.equals("/app.js")&&method.equals("GET")){
                    send(ex,200,"text/javascript",Files.readString(Path.of("web","app.js")));return;
                }
                send(ex,404,"application/json","{\"error\":\"Not found\"}");
            } catch(IllegalArgumentException e){send(ex,400,"application/json","{\"error\":\""+jsonEscape(e.getMessage())+"\"}");}
              catch(Exception e){send(ex,500,"application/json","{\"error\":\"Server error\"}");}
            finally{ex.close();}
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Task server listening at http://127.0.0.1:"+port);
    }
}
