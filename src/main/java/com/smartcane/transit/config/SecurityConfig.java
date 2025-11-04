package com.smartcane.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    //    @Bean
//    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(csrf -> csrf.ignoringRequestMatchers(
//                        new AntPathRequestMatcher("/v3/api-docs/**"),
//                        new AntPathRequestMatcher("/swagger-ui/**"),
//                        new AntPathRequestMatcher("/swagger-ui.html"),
//                        new AntPathRequestMatcher("/docs"),
//                        new AntPathRequestMatcher("/docs/**")
//                ))
//                .cors(Customizer.withDefaults())
//                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
//                .authorizeHttpRequests(auth -> auth
//                        // ✅ 문서/헬스/공개 API는 모두 허용
//                        .requestMatchers(
//                                "/v3/api-docs/**",
//                                "/swagger-ui/**",
//                                "/swagger-ui.html",
//                                "/docs", "/docs/**",
//                                "/actuator/health",
//                                "/api/**"            // 원하신 공개 API
//                        ).permitAll()
//                        .anyRequest().authenticated()
//                )
//                // 필요 없다면 아래 둘 다 비활성
//                .httpBasic(AbstractHttpConfigurer::disable)
//                .formLogin(AbstractHttpConfigurer::disable);
//
//        return http.build();
//    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 전체 비활성(세션 없는 API/문서용이면 이게 단순하고 안전)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/docs", "/docs/**",
                                "/actuator/health",
                                "/api/**",
                                "/transit/**"// 공개하려는 엔드포인트면 유지
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // 기본 인증/폼 로그인 비활성
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // CORS (필요시 ALB 도메인만 허용하도록 바꾸세요)
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("*")); // 예: "https://admin.your-domain.com"
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
