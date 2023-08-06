package me.sonam.auth.repo;

import me.sonam.auth.repo.entity.Client;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ClientRepository extends ReactiveCrudRepository<Client, String> {
    Mono<Integer> countByClientId(String clientId);
}
