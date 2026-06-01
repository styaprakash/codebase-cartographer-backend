package com.codebasecartographer.api.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codebasecartographer.api.dto.response.RepoResponse;
import com.codebasecartographer.api.entity.Repository;
import com.codebasecartographer.api.entity.User;
import com.codebasecartographer.api.enums.RepositoryStatus;
import com.codebasecartographer.api.exception.ResourceNotFoundException;
import com.codebasecartographer.api.repository.CodeChunkRepository;
import com.codebasecartographer.api.repository.RepositoryRepository;
import com.codebasecartographer.api.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RepoService {
    // We need RepositoryRepository to talk to the database
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final CodeChunkRepository codeChunkRepository;

    // Constructor injection
    public RepoService(RepositoryRepository repositoryRepository, UserRepository userRepository, CodeChunkRepository codeChunkRepository){
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.codeChunkRepository = codeChunkRepository;
    }

    // get all repos for a user
    public List<RepoResponse> getAllRepos(String userId){
        //Fetch all repositories for this user
        List<Repository> repos = repositoryRepository.findByUserId(userId);

        // Convert List<Repository> -> List<RepoResponse>
        return repos
            .stream()
            .map(repo -> repoResponse(repo))
            .collect(Collectors.toList());
    }

    // get ONE repo — scoped to user for security
    public RepoResponse getRepoById(String userId, String repoId){
        Repository repo = repositoryRepository
            .findByUserIdAndId(userId, repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId)
        );

        return repoResponse(repo);
    }


    // import a repo — called when user clicks "Index repo"
    // Upsert: if repo already exists for this user, update metadata; otherwise create new
    public RepoResponse createRepo(String userId, String githubRepoId, String name, String fullName, String branch, String language){
        // Check if this repo already exists for this user
        var existing = repositoryRepository.findByUserIdAndGithubRepoId(userId, githubRepoId);

        if(existing.isPresent()){
            log.info("Repo already exists — updating metadata: {}", fullName);
            Repository repo = existing.get();
            repo.setName(name);
            repo.setFullName(fullName);
            repo.setBranch(branch);
            repo.setLanguage(language);
            Repository saved = repositoryRepository.save(repo);
            return repoResponse(saved);
        }

        // Find the user first (you need the User object for the FK)
        User user = userRepository.findById(userId)
            .orElseThrow( () -> {
                log.warn("User not found in createRepo: {}", userId);
                return new ResourceNotFoundException("User", "id", userId);
            });


        //Build new repo entity with PENDING status
        Repository newRepo = Repository.builder()
            .user(user)
            .githubRepoId(githubRepoId)
            .name(name)
            .fullName(fullName)
            .branch(branch)
            .language(language)
            .status(RepositoryStatus.PENDING) // always starts as PENDING
            .totalFiles(0)
            .indexedFiles(0)
            .build();

        Repository saved = repositoryRepository.save(newRepo);

        //convert the new repo entity to dto then return
        return repoResponse(saved);

    }

    // update metadata (name, fullName, branch, language) — used on re-index after rename
    @Transactional
    public void updateRepoMetadata(String repoId, String name, String fullName, String branch, String language){
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));
        repo.setName(name);
        repo.setFullName(fullName);
        repo.setBranch(branch);
        if(language != null) repo.setLanguage(language);
        repositoryRepository.save(repo);
        log.info("Updated metadata for repo {}: {}", repoId, fullName);
    }

    //update status : Called by indexing worker: PENDING → INDEXING → INDEXED/FAILED
    @Transactional
    public void updateStatus(String repoId, RepositoryStatus status){
        //check if the repo exist
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "Id", repoId));

        repo.setStatus(status);

        //If indexing complete -> record the timestamp
        if(status == RepositoryStatus.INDEXED){
            repo.setIndexedAt(LocalDateTime.now());
        }

        repositoryRepository.save(repo);
    }

    // updateProgress: Called during indexing — frontend polls for progress bar update
    @Transactional
    public void updateProgress(String repoId, int indexedFiles, int totalFiles){
        //check if the repo exist
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        repo.setIndexedFiles(indexedFiles);
        repo.setTotalFiles(totalFiles);
        repositoryRepository.save(repo);
    }

    @Transactional
    protected void prepareIndexing(String repoId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        long existingChunks = codeChunkRepository.countByRepository_Id(repoId);
        if (existingChunks > 0) {
            codeChunkRepository.deleteByRepository_Id(repoId);
        }

        repo.setIndexedFiles(0);
        repo.setTotalFiles(0);
        repo.setErrorMessage(null);
        repo.setStatus(RepositoryStatus.INDEXING);
        repositoryRepository.save(repo);
    }


    // setErrorMessage: Called when indexing fails — stores why it failed
    @Transactional
    public void setErrorMessage(String repoId, String errorMessage){
        //check if the repo exist
        Repository repo = repositoryRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repoId));

        repo.setErrorMessage(errorMessage);
        repo.setStatus(RepositoryStatus.FAILED);
        repositoryRepository.save(repo);
    }

    // Private Helper : Converts Repositry Entity -> RepoResponse dto
    private RepoResponse repoResponse(Repository repo){
            return RepoResponse.builder()
                .id(repo.getId())
                .userId(repo.getUser().getId())
                .githubRepoId(repo.getGithubRepoId())
                .name(repo.getName())
                .fullName(repo.getFullName())
                .branch(repo.getBranch())
                .language(repo.getLanguage())
                .status(repo.getStatus())
                .totalFiles(repo.getTotalFiles())
                .indexedFiles(repo.getIndexedFiles())
                .errorMessage(repo.getErrorMessage())
                .indexedAt(repo.getIndexedAt())
                .createdAt(repo.getCreatedAt())
                .build();
    }
}

