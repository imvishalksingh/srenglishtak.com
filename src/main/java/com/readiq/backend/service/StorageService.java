package com.readiq.backend.service;
import com.readiq.backend.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Service
public class StorageService {
    private final String url,key;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public StorageService(@Value("${supabase.url}")String url,@Value("${supabase.service-role-key}")String key){this.url=url.replaceAll("/+$","");this.key=key;}
    public String upload(MultipartFile file){
        if(file.isEmpty())throw ApiException.bad("No file uploaded");
        if(file.getSize()>50L*1024*1024)throw new ApiException(413,"File exceeds 50 MiB");
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')):"";
        String type=switch(ext){case ".pdf"->"application/pdf";case ".epub"->"application/epub+zip";case ".png"->"image/png";
            case ".jpg",".jpeg"->"image/jpeg";case ".webp"->"image/webp";case ".gif"->"image/gif";default->throw ApiException.bad("Unsupported file type");};
        try{
            byte[] bytes=file.getBytes();
            if(!signature(ext,bytes))throw ApiException.bad("File contents do not match the selected file type");
            String object=UUID.randomUUID()+ext;
            var request=HttpRequest.newBuilder(URI.create(url+"/storage/v1/object/books/"+object)).timeout(Duration.ofSeconds(60))
                .header("Authorization","Bearer "+key).header("apikey",key).header("Content-Type",type).header("x-upsert","false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()!=200&&response.statusCode()!=201)throw new ApiException(502,"Storage upload failed");
            return url+"/storage/v1/object/public/books/"+object;
        }catch(ApiException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ApiException(502,"Storage upload failed");}
        catch(Exception ex){throw new ApiException(502,"Storage upload failed");}
    }
    private boolean signature(String ext,byte[] b){
        if(b.length<12)return false;String prefix=new String(b,0,Math.min(12,b.length),StandardCharsets.ISO_8859_1);
        return switch(ext){
            case ".pdf"->prefix.startsWith("%PDF-");
            case ".epub"->b[0]=='P'&&b[1]=='K'&&b[2]==3&&b[3]==4;
            case ".png"->Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
            case ".jpg",".jpeg"->b[0]==(byte)255&&b[1]==(byte)216&&b[2]==(byte)255;
            case ".gif"->prefix.startsWith("GIF87a")||prefix.startsWith("GIF89a");
            case ".webp"->prefix.startsWith("RIFF")&&prefix.substring(8).equals("WEBP");
            default->false;
        };
    }
    public InputStream download(String publicUrl){
        URI configured=URI.create(url),file;
        try{file=URI.create(publicUrl);}catch(Exception ex){throw ApiException.bad("Invalid stored file URL");}
        // Service credentials are sent only to our own Supabase bucket. No redirects or arbitrary URLs.
        if(!Objects.equals(configured.getScheme(),file.getScheme())||!Objects.equals(configured.getHost(),file.getHost())||
            configured.getPort()!=file.getPort()||file.getRawQuery()!=null||file.getUserInfo()!=null||
            !file.getPath().startsWith("/storage/v1/object/public/books/")||file.getPath().contains("..")||file.getRawPath().contains("%"))
            throw ApiException.bad("File cannot be streamed from this storage location");
        try{
            String path=file.getPath().replace("/object/public/","/object/authenticated/");
            var response=client.send(HttpRequest.newBuilder(URI.create(url+path)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+key)
                .header("apikey",key).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode()!=200){response.body().close();throw new ApiException(502,"File download failed");}
            return response.body();
        }catch(ApiException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ApiException(502,"File download failed");}
        catch(Exception ex){throw new ApiException(502,"File download failed");}
    }
}
