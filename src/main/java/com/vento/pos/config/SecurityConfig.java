package com.vento.pos.config;

import com.vento.pos.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return username -> usuarioRepository.findByUsername(username)
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .map(usuario -> User.withUsername(usuario.getUsername())
                        .password(usuario.getPassword() == null ? "{noop}" : usuario.getPassword())
                        .authorities(usuario.getRol().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario inválido o inactivo."));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/login", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/logs/**").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/auth/password").authenticated()
                        .requestMatchers("/api/v1/auth/discount-authorization", "/api/v1/auth/autorizar-descuento").authenticated()
                        .requestMatchers(
                                "/api/v1/promotions/quote", "/api/v1/promotions/active",
                                "/api/v1/promociones/quote", "/api/v1/promociones/active").authenticated()
                        .requestMatchers(
                                "/api/v1/promotions", "/api/v1/promotions/**",
                                "/api/v1/promociones", "/api/v1/promociones/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/turnos/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ventas", "/api/v1/pos/cobrar").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/configuracion").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/configuracion").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/configuracion/dte").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dte/configuracion").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/dte/configuracion").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/dte/ventas/*/firmar").authenticated()
                        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/proveedores", "/api/v1/proveedores/**",
                                "/api/v1/compras", "/api/v1/compras/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/clientes/**").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(
                                "/api/v1/productos", "/api/v1/productos/**",
                                "/api/v1/categorias", "/api/v1/categorias/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/reportes/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/inventario/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/ventas/*/devolucion").hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**"))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));

        return http.build();
    }
}
