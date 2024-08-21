package startup.commands.config;

import aniAdd.config.AniConfiguration;
import aniAdd.config.AniConfigurationHandler;
import config.CliConfiguration;
import config.CliConfiguration.*;
import config.CliConfiguration.AniDbConfig.CacheConfig;
import config.CliConfiguration.MoveConfig.HandlingConfig;
import config.CliConfiguration.MyListConfig.StorageType;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import startup.validation.validators.nonblank.NonBlank;
import utils.config.ConfigFileHandler;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "convert",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Converts a config file to a new format")
public class ConvertCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false, scope = CommandLine.ScopeType.INHERIT)
    private String taggingSystem;

    @NonBlank
    @CommandLine.Parameters(index = "0", description = "The path to the legacy config file (AniConfiguration)")
    Path configPath;

    @NonBlank
    @CommandLine.Parameters(index = "1", description = "The path to the file to save the configuration to.")
    private Path path;

    @Override
    public Integer call() throws Exception {
        val handler = new AniConfigurationHandler(taggingSystem);
        val config = handler.getConfiguration(configPath);
        if (config == null) {
            log.error(STR."Failed to convert configuration. Could not load from \{configPath}");
            return 1;
        }
        val cliConfig = CliConfiguration.builder()
                .tagSystem(config.getTagSystemCode())
                .anidb(AniDbConfig.builder()
                        .host(config.getAnidbHost())
                        .port(config.getAnidbPort())
                        .cache(CacheConfig.builder().ttlInDays(config.getCacheTTLInDays()).build())
                        .build())
                .mylist(MyListConfig.builder()
                        .add(config.isAddToMylist())
                        .overwrite(config.isOverwriteMLEntries())
                        .storageType(convertStorageType(config.getSetStorageType()))
                        .build())
                .rename(RenameConfig.builder()
                        .related(config.isRenameRelatedFiles())
                        .mode(getRenameType(config))
                        .build())
                .move(getMoveConfig(config))
                .paths(getPathConfig(config))
                .build();

        val cliHandler = new ConfigFileHandler<>(CliConfiguration.class);
        cliConfig.removeDefaults();
        cliHandler.saveTo(path, cliConfig);
        return 0;
    }

    private PathConfig getPathConfig(AniConfiguration config) {
        val builder = PathConfig.builder();
        var hasPaths = false;
        if (config.getMovieFolder() != null && !config.getMovieFolder().isBlank()) {
            builder.movieFolder(PathConfig.Single.builder()
                    .tagSystemName("BaseMoviePath")
                    .path(Path.of(config.getMovieFolder()))
                    .build());
            hasPaths = true;
        }
        if (config.getTvShowFolder() != null && !config.getTvShowFolder().isBlank()) {
            builder.tvShowFolder(PathConfig.Single.builder()
                    .tagSystemName("BaseTVShowPath")
                    .path(Path.of(config.getTvShowFolder()))
                    .build());
            hasPaths = true;
        }
        if (hasPaths) {
            return builder.build();
        }
        return null;
    }

    private MoveConfig getMoveConfig(AniConfiguration config) throws IllegalArgumentException {
        val type = getMoveType(config);
        if (type == MoveConfig.Mode.FOLDER && config.getMoveToFolder().isBlank()) {
            throw new IllegalArgumentException("Move type is set to FOLDER but no folder is specified. (moveTypeUseFolder: true, moveToFolder: \"\")");
        }
        return MoveConfig.builder()
                .mode(type)
                .folder(Path.of(config.getMoveToFolder()))
                .deleteEmptyDirs(config.isRecursivelyDeleteEmptyFolders())
                .duplicates(HandlingConfig.builder()
                        .mode(getDuplicatesType(config))
                        .folder(Path.of(config.getDuplicatesFolder()))
                        .build())
                .unknown(HandlingConfig.builder()
                        .mode(getUnknownType(config))
                        .folder(Path.of(config.getUnknownFolder()))
                        .build())
                .build();
    }

    private HandlingConfig.Mode getUnknownType(AniConfiguration config) {
        if (config.isMoveUnknownFiles()) {
            return HandlingConfig.Mode.MOVE;
        }
        return HandlingConfig.Mode.IGNORE;
    }

    private HandlingConfig.Mode getDuplicatesType(AniConfiguration config) {
        if (config.isMoveDuplicateFiles()) {
            return HandlingConfig.Mode.MOVE;
        }
        if (config.isDeleteDuplicateFiles()) {
            return HandlingConfig.Mode.DELETE;
        }
        return HandlingConfig.Mode.IGNORE;
    }

    private MoveConfig.Mode getMoveType(AniConfiguration config) {
        if (!config.isEnableFileMove()) {
            return MoveConfig.Mode.NONE;
        } else if (config.isMoveTypeUseFolder()) {
            return MoveConfig.Mode.FOLDER;
        } else {
            return MoveConfig.Mode.TAGSYSTEM;
        }
    }

    private RenameConfig.Mode getRenameType(AniConfiguration config) {
        if (!config.isEnableFileRenaming()) {
            return RenameConfig.Mode.NONE;
        } else if (config.isRenameTypeAniDBFileName()) {
            return RenameConfig.Mode.ANIDB;
        } else {
            return RenameConfig.Mode.TAGSYSTEM;
        }
    }

    private StorageType convertStorageType(AniConfiguration.StorageType storageType) {
        return switch (storageType) {
            case UNKNOWN, UNKOWN -> StorageType.UNKNOWN;
            case INTERNAL -> StorageType.INTERNAL;
            case EXTERNAL -> StorageType.EXTERNAL;
            case DELETED -> StorageType.DELETED;
            case REMOTE -> StorageType.REMOTE;
        };
    }
}
