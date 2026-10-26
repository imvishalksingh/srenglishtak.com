package com.readiq.backend.service;
import com.readiq.backend.api.*;
import com.readiq.backend.data.Database;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.readiq.backend.api.Input.*;
@Service
public class CatalogService {
    private record Spec(String fields,String required,String order){}
    private static final Map<String,Spec>SPECS=Map.of(
        "books",new Spec("title author description category format_type file_url cover_url is_visible","title","created_at desc,id"),
        "grammar_units",new Spec("title description unit_order image_url","title unit_order","unit_order,id"),
        "grammar_lessons",new Spec("title content_type content_data lesson_order","title lesson_order","lesson_order,id"),
        "cbse_categories",new Spec("name description icon_url order_index","name","order_index,id"),
        "cbse_materials",new Spec("material_type title year description file_url thumbnail_url","title file_url","created_at desc,id"),
        "vocabulary",new Spec("word meaning example_sentence category","word meaning","created_at desc,id"),
        "daily_tips",new Spec("title content author image_url","title content","created_at desc,id"));
    private final Database db;
    public CatalogService(Database db){this.db=db;}
    public List<Map<String,Object>> list(String table,String foreign,UUID parent){
        Spec spec=spec(table);
        return db.list("select * from "+table+(foreign==null?"":" where "+foreign+"=?")+" order by "+spec.order,
            foreign==null?new Object[]{}:new Object[]{parent});
    }
    public List<Map<String,Object>> books(boolean admin){return db.list("select * from books"+(admin?"":" where is_visible=true")+" order by created_at desc,id");}
    public Map<String,Object> book(UUID id,boolean admin){
        var book=db.byId("books",id);if(!admin&&!Boolean.TRUE.equals(book.get("is_visible")))throw ApiException.missing();return book;
    }
    @Transactional
    public Map<String,Object> save(String table,UUID id,Map<String,Object> body,Map<String,Object> serverFields){
        var spec=spec(table);var values=fields(body,spec.fields.split(" "));
        for(String required:spec.required.split(" ")){
            if(id==null||body.containsKey(required)){
                Object v=values.get(required);
                if(v==null||(v instanceof String s&&s.isBlank()))throw ApiException.bad(required+" is required");
            }
        }
        choice(values,"format_type","pdf","epub");choice(values,"content_type","text","pdf","video");
        choice(values,"material_type","model_paper","pyq","notes","syllabus");
        if(serverFields!=null)values.putAll(serverFields);
        return id==null?db.insert(table,values):db.update(table,id,values);
    }
    @Transactional public List<Map<String,Object>> bulk(String table,List<Map<String,Object>> rows,Map<String,Object> extra){
        if(rows.isEmpty()||rows.size()>500)throw ApiException.bad("Supply between 1 and 500 records");
        return rows.stream().map(b->save(table,null,b,extra)).toList();
    }
    public void delete(String table,UUID id){spec(table);db.delete(table,id);}
    private Spec spec(String table){Spec s=SPECS.get(table);if(s==null)throw new IllegalArgumentException("Invalid catalog");return s;}
}
