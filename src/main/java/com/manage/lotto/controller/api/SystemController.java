package com.manage.lotto.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(path = "/system")
@RequiredArgsConstructor
public class SystemController {

//    @PostMapping(path = "/manual/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<String> manualExcel(@RequestParam("file") MultipartFile file) throws IOException {
//
//    }
}
