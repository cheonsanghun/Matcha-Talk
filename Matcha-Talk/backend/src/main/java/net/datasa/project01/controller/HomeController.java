package net.datasa.project01.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // "/" 경로로 접근 시 index.html 반환 (static 폴더 사용 시 별도 코드 필요 없음)
    @GetMapping("/")
    public String home() {
        // templates/index.html 사용 시 "index" 반환
        return "index";
    }
}