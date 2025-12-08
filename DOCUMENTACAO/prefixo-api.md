# Por que usar o prefixo `/api` em Controllers REST?

## Contexto

No projeto, o `ProdutoController` utiliza o caminho `/api/produtos` através da anotação:

```java
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    // ...
}
```

Este documento explica os motivos dessa escolha.

## Principais Motivos

### 1. **Separação de Responsabilidades**
O prefixo `/api` distingue claramente os endpoints de API REST de outras rotas da aplicação, como:
- Páginas web (caso tenha frontend integrado)
- Recursos estáticos (CSS, JavaScript, imagens)
- Documentação (Swagger UI geralmente usa `/swagger-ui`)

**Exemplo:**
- `/api/produtos` → API REST
- `/produtos` → Página web para usuários
- `/static/logo.png` → Recursos estáticos

### 2. **Versionamento Futuro**
Facilita a implementação de versionamento de API sem quebrar clientes existentes:

```java
@RequestMapping("/api/v1/produtos")  // Versão 1
@RequestMapping("/api/v2/produtos")  // Versão 2 (com breaking changes)
```

### 3. **Configuração de Segurança**
Permite aplicar regras específicas apenas para rotas de API:

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()  // API requer autenticação
                .anyRequest().permitAll()                    // Resto público
            )
            .csrf().disable(); // Desabilita CSRF apenas para /api/*
        return http.build();
    }
}
```

### 4. **CORS (Cross-Origin Resource Sharing)**
Facilita configuração de CORS específica para API:

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")  // CORS apenas para API
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
```

### 5. **Proxy e API Gateway**
Em ambientes com proxy reverso (Nginx, Apache) ou API Gateway, facilita o roteamento:

```nginx
# nginx.conf
location /api/ {
    proxy_pass http://backend-server:8080;
}

location / {
    proxy_pass http://frontend-server:3000;
}
```

### 6. **Clareza e Convenção**
É uma **convenção amplamente adotada** na comunidade:
- Desenvolvedores identificam imediatamente que se trata de uma API REST
- Facilita onboarding de novos membros da equipe
- Melhora a documentação e comunicação

### 7. **Logging e Monitoramento**
Permite configurar logs e métricas específicas para chamadas de API:

```properties
# application.properties
logging.level.org.springframework.web.servlet.mvc.method.annotation=/api/**=DEBUG
```

## Alternativas

### Remover o prefixo
```java
@RequestMapping("/produtos")
```
✅ **Vantagem:** URLs mais curtas  
❌ **Desvantagem:** Menos flexibilidade e organização

### Configurar globalmente no application.properties
```properties
server.servlet.context-path=/api
```

```java
@RequestMapping("/produtos")  // Acesso: /api/produtos
```
✅ **Vantagem:** Centraliza a configuração  
❌ **Desvantagem:** Afeta TODA a aplicação

### Usar versionamento direto
```java
@RequestMapping("/v1/produtos")
```
✅ **Vantagem:** Preparado para múltiplas versões  
❌ **Desvantagem:** Pode ser excessivo se não houver necessidade imediata

## Endpoints do Projeto

Com o prefixo `/api`, os endpoints ficam:

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/produtos` | Lista todos os produtos |
| GET | `/api/produtos?nome=xyz` | Busca por nome |
| GET | `/api/produtos?apenasAtivos=true` | Lista apenas ativos |
| GET | `/api/produtos/{id}` | Busca por ID |
| POST | `/api/produtos` | Cria novo produto |
| PUT | `/api/produtos/{id}` | Atualiza produto |
| DELETE | `/api/produtos/{id}` | Remove produto (exclusão física) |
| PATCH | `/api/produtos/{id}/inativar` | Inativa produto (exclusão lógica) |

## Conclusão

O prefixo `/api` **não é obrigatório**, mas é uma **boa prática recomendada** que traz benefícios significativos em termos de:
- Organização
- Escalabilidade
- Segurança
- Manutenibilidade

A escolha foi feita seguindo as convenções da comunidade Spring Boot e REST API em geral.

---

**Autor:** GitHub Copilot  
**Data:** 03/12/2025  
**Projeto:** Demo - Sistema de Produtos

