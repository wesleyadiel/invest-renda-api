package com.bancointer.investrenda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache de produtos em Redis: evita bater no Postgres a cada consulta de
 * listagem/detalhe, ja que os dados de produto mudam raramente.
 */
@Configuration
public class ConfiguracaoCacheRedis {

    public static final String CACHE_PRODUTOS = "produtos";
    public static final String CACHE_PRODUTO = "produto";

    @Bean
    public RedisCacheManagerBuilderCustomizer personalizadorCacheManagerRedis() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // EVERYTHING (nao NON_FINAL): os DTOs de resposta sao records, que sao
        // implicitamente final e ficariam sem informacao de tipo no NON_FINAL,
        // quebrando a desserializacao (que sempre espera o wrapper de tipo).
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        return builder -> builder
                .withCacheConfiguration(CACHE_PRODUTOS,
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)))
                .withCacheConfiguration(CACHE_PRODUTO,
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }
}
