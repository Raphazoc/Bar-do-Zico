package br.com.bardozico;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/setup")
class SetupController {
    private final JdbcTemplate db;private final ObjectMapper json;private final List<String> addresses;
    private final Path certificate,backups;private final boolean training;
    SetupController(JdbcTemplate db,ObjectMapper json,@Value("${bar.android-urls}") String urls,
            @Value("${bar.public-certificate}") String certificate,@Value("${bar.backup-directory}") String backups,
            @Value("${bar.training}") boolean training) {
        this.db=db;this.json=json;this.training=training;
        this.addresses=Arrays.stream(urls.split(",")).filter(s->s.matches("https://[0-9.]+:[0-9]+") && s.length()<100).toList();
        this.certificate=certificate.isBlank()?null:Path.of(certificate);
        this.backups=backups.isBlank()||training?null:Path.of(backups);
    }
    @GetMapping Map<String,Object> setup(@AuthenticationPrincipal Actor actor) {
        actor.require("user.manage");var out=new LinkedHashMap<String,Object>();
        out.put("training",training);out.put("addresses",addresses);
        out.put("certificateAvailable",certificate!=null&&Files.isRegularFile(certificate));
        var counts=new LinkedHashMap<String,Integer>();
        for(String table:List.of("areas","bar_tables","categories","products","cash_terminals"))
            counts.put(table,db.queryForObject("SELECT count(*) FROM bar."+table+" WHERE establishment_id=?"+(table.equals("bar_tables")?" AND active":""),Integer.class,actor.unit()));
        out.put("counts",counts);
        out.put("backups",backupInfo());return out;
    }
    private Map<String,Object> backupInfo() {
        var info=new LinkedHashMap<String,Object>();info.put("available",backups!=null);
        if(backups==null)return info;
        var status=read("status.json");var config=read("config.json");var worker=read("worker.json");
        for(String name:List.of("state","lastSuccessAt","file","bytes","verified","secondaryState","error"))
            if(status.get(name)!=null)info.put(name,status.get(name));
        info.put("enabled",Boolean.TRUE.equals(config.get("enabled")));
        info.put("intervalMinutes",config.getOrDefault("intervalMinutes",60));
        info.put("secondaryConfigured",config.get("secondaryDirectory")!=null);
        info.put("requested",Files.exists(backups.resolve("request.json")));
        boolean active=false;
        try {active=Boolean.TRUE.equals(worker.get("enabled"))&&Instant.parse(worker.get("heartbeatAt").toString()).isAfter(Instant.now().minusSeconds(180));}
        catch(Exception ignored) { }
        info.put("workerActive",active);return info;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> read(String name) {
        try {Path path=backups.resolve(name);if(!Files.isRegularFile(path)||Files.size(path)>65536)return Map.of();return json.readValue(Files.readString(path),Map.class);}
        catch(Exception e) {return Map.of();}
    }
    @GetMapping(value="/qr",produces="image/svg+xml") String qr(@AuthenticationPrincipal Actor actor,@RequestParam String address) throws Exception {
        actor.require("user.manage");if(!addresses.contains(address))throw ApiError.bad("Escolha um dos endereços locais informados pelo sistema.");
        var matrix=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,256,256);
        var svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 256 256\"><rect width=\"256\" height=\"256\" fill=\"white\"/><path fill=\"black\" d=\"");
        for(int y=0;y<256;y++)for(int x=0;x<256;x++)if(matrix.get(x,y))svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        return svg.append("\"/></svg>").toString();
    }
    @GetMapping("/certificate") ResponseEntity<byte[]> certificate(@AuthenticationPrincipal Actor actor) throws Exception {
        actor.require("user.manage");
        if(certificate==null||!Files.isRegularFile(certificate)||Files.size(certificate)>65536)throw new ApiError(404,"Certificado público ainda não preparado.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=bar-ca.cer").body(Files.readAllBytes(certificate));
    }
    @PostMapping("/backup") ResponseEntity<?> requestBackup(@AuthenticationPrincipal Actor actor) throws Exception {
        actor.require("user.manage");
        if(backups==null)throw new ApiError(503,"Use o ambiente de operação para administrar os backups reais.");
        if(!Boolean.TRUE.equals(read("config.json").get("enabled")))throw new ApiError(409,"Ative os backups automáticos no computador antes de solicitar pela tela.");
        Files.createDirectories(backups);
        var path=Files.createTempFile(backups,"request-",".tmp");
        try {
            json.writeValue(path.toFile(),Map.of("requestedAt",Instant.now().toString(),"actorId",actor.id().toString()));
            Files.move(path,backups.resolve("request.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(path);}
        return ResponseEntity.accepted().body(Map.of("requested",true));
    }
}
