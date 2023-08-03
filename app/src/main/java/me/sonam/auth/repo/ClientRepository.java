package me.sonam.auth.repo;

import me.sonam.auth.repo.entity.Client;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ClientRepository extends ReactiveCrudRepository<Client, String> {
}
