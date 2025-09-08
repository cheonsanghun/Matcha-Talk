package net.datasa.project01.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * 루트 경로("/")로 접근 시 "/login.html"로 리디렉션
     * @return "redirect:/login.html"
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/login.html";
    }
}