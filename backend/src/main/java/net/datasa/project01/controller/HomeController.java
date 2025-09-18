package net.datasa.project01.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping(value = {
            "/",
            "/{path:^(?!api|ws-stomp|actuator|swagger-ui|v3).*$}",
            "/{path:^(?!api|ws-stomp|actuator|swagger-ui|v3).*$}/**"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
