package com.vento.pos.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping(value = "/login", produces = "text/html")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            CsrfToken csrfToken
    ) {
        String message = error != null
                ? "<p class=\"message error\">Usuario o contraseña incorrectos.</p>"
                : logout != null
                ? "<p class=\"message\">Sesión cerrada. Vuelve a ingresar.</p>"
                : "";
        return """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Iniciar sesión | Vento</title>
                    <style>
                        *{box-sizing:border-box}
                        body{font:15px "Segoe UI",sans-serif;color:#242a36;background:#f5f6f8;display:grid;min-height:100vh;place-items:center;margin:0}
                        main{width:min(100%% - 32px,390px);padding:34px;border:1px solid #eceef1;border-radius:14px;background:#fff;box-shadow:0 12px 35px #242a3610}
                        .brand{margin:0;color:#242a36;font-size:30px;font-weight:800;letter-spacing:-1.5px}
                        .brand span{color:#e8794e}
                        h1{margin:28px 0 7px;font-size:22px}
                        .intro{margin:0 0 24px;color:#777d87;font-size:13px}
                        label{display:block;margin:15px 0 7px;font-size:12px;font-weight:600}
                        input{width:100%%;height:42px;padding:0 12px;border:1px solid #dfe2e7;border-radius:7px;font:inherit}
                        button{width:100%%;height:44px;margin-top:22px;border:0;border-radius:7px;background:#e8794e;color:#fff;font:600 14px "Segoe UI",sans-serif;cursor:pointer}
                        button:hover{background:#d9653b}
                        .message{padding:10px 12px;border-radius:7px;background:#eef7f1;color:#38724f;font-size:12px}
                        .error{background:#fdf0ed;color:#ad4b35}
                    </style>
                </head>
                <body>
                    <main>
                        <p class="brand">vento<span>.</span></p>
                        <h1>Iniciar sesión</h1>
                        <p class="intro">Ingresa tu usuario para continuar.</p>
                        %s
                        <form method="post" action="/login">
                            <input type="hidden" name="%s" value="%s">
                            <label for="username">Usuario</label>
                            <input id="username" name="username" type="text" autocomplete="username" required autofocus>
                            <label for="password">Contraseña</label>
                            <input id="password" name="password" type="password" autocomplete="current-password">
                            <button type="submit">Entrar a Vento</button>
                        </form>
                    </main>
                </body>
                </html>
                """.formatted(message, csrfToken.getParameterName(), csrfToken.getToken());
    }
}
