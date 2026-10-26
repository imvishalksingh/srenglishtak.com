package com.readiq.backend.data;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readiq.backend.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class Database {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final Set<String> TABLES=Set.of("profiles","books","quizzes","questions","user_progress",
        "reading_sessions","bookmarks","notes","user_achievements","quiz_results","quiz_attempts","vocabulary",
        "daily_tips","challenges","challenge_questions","challenge_results","grammar_units","grammar_lessons",
        "grammar_progress","cbse_categories","cbse_materials","module_interests","challenge_attempt_sessions");
    private static final Set<String> JSON_FIELDS=Set.of("options","answers_json");
    public Database(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    private Map<String,Object> mapRow(ResultSet rs,int n)throws SQLException{
        Map<String,Object> row=new LinkedHashMap<>();ResultSetMetaData meta=rs.getMetaData();
        for(int i=1;i<=meta.getColumnCount();i++){
            Object v=rs.getObject(i);
            if(v instanceof Timestamp t)v=t.toInstant().toString();
            else if(v instanceof OffsetDateTime t)v=t.toInstant().toString();
            else if(v instanceof UUID)v=v.toString();
            else if("jsonb".equals(meta.getColumnTypeName(i))||"json".equals(meta.getColumnTypeName(i))){
                try{v=v==null?null:json.readValue(v.toString(),Object.class);}
                catch(Exception ex){throw new SQLException("Invalid stored JSON",ex);}
            }
            row.put(meta.getColumnLabel(i),v);
        }
        return row;
    }
    public List<Map<String,Object>> list(String sql,Object... args){return jdbc.query(sql,this::mapRow,args);}
    public Map<String,Object> one(String sql,Object... args){
        var rows=list(sql,args);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public Optional<Map<String,Object>> optional(String sql,Object... args){return list(sql,args).stream().findFirst();}
    public long count(String sql,Object... args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    public int execute(String sql,Object... args){return jdbc.update(sql,args);}
    public Map<String,Object> byId(String table,UUID id){return one("select * from "+table(table)+" where id=?",id);}
    public Map<String,Object> insert(String table,Map<String,Object> values){
        table=table(table);if(values.isEmpty())throw ApiException.bad("Empty request");
        var fields=new ArrayList<>(values.keySet());fields.forEach(Database::column);
        String placeholders=fields.stream().map(f->JSON_FIELDS.contains(f)?"cast(? as jsonb)":"?").collect(Collectors.joining(","));
        Object[] args=fields.stream().map(f->bind(f,values.get(f))).toArray();
        return one("insert into "+table+" ("+String.join(",",fields)+") values ("+placeholders+") returning *",args);
    }
    public Map<String,Object> update(String table,UUID id,Map<String,Object> values){
        table=table(table);if(values.isEmpty())return byId(table,id);
        var fields=new ArrayList<>(values.keySet());fields.forEach(Database::column);
        String assignments=fields.stream().map(f->f+"="+(JSON_FIELDS.contains(f)?"cast(? as jsonb)":"?")).collect(Collectors.joining(","));
        var args=new ArrayList<Object>();for(String f:fields)args.add(bind(f,values.get(f)));args.add(id);
        return one("update "+table+" set "+assignments+" where id=? returning *",args.toArray());
    }
    public void delete(String table,UUID id){if(execute("delete from "+table(table)+" where id=?",id)==0)throw ApiException.missing();}
    private Object bind(String field,Object value){
        if(!JSON_FIELDS.contains(field)||value==null)return value;
        try{return json.writeValueAsString(value);}catch(Exception ex){throw ApiException.bad("Invalid JSON");}
    }
    private static String table(String table){if(!TABLES.contains(table))throw new IllegalArgumentException("Unknown table");return table;}
    private static void column(String column){if(!column.matches("[a-z_]+"))throw new IllegalArgumentException("Invalid column");}
}
