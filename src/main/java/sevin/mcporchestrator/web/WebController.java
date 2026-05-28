package sevin.mcporchestrator.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/backoffice"})
    public String backoffice() { return "backoffice"; }

    @GetMapping("/web")
    public String web() { return "web"; }
}
