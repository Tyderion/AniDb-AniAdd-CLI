package aniAdd.startup.commands.config;

import aniAdd.config.AniConfiguration;
import aniAdd.config.AniConfigurationHandler;
import config.CliConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import utils.config.ConfigFileHandler;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(name = "convert",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Converts a config file to a new format")
public class ConvertCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the legacy config file (AniConfiguration)", required = false, scope = CommandLine.ScopeType.INHERIT)
    Path configPath;

    @CommandLine.Option(names = {"--tagging-system"}, description = "the path to a file containing the Tagging System definition", required = false, scope = CommandLine.ScopeType.INHERIT)
    private String taggingSystem;

    @CommandLine.Parameters(index = "0", description = "The path to the file to save the configuration to.")
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
                .anidb(CliConfiguration.AniDbConfig.builder()
                        .host(config.getAnidbHost())
                        .port(config.getAnidbPort())
                        .cache(CliConfiguration.AniDbConfig.CacheConfig.builder().ttlInDays(config.getCacheTTLInDays()).build())
                        .build())
                .mylist(CliConfiguration.MyListConfig.builder()
                        .add(config.isAddToMylist())
                        .overwrite(config.isOverwriteMLEntries())
                        .storageType(convertStorageType(config.getSetStorageType()))
                        .build())
                .rename(CliConfiguration.RenameConfig.builder()
                        .related(config.isRenameRelatedFiles())
                        .type(getRenameType(config))
                        .build())
                .move(getMoveConfig(config))
                .build();

        val cliHandler = new ConfigFileHandler<>(CliConfiguration.class);
        cliHandler.saveTo(path, cliConfig);
        return 0;
    }

    private CliConfiguration.MoveConfig getMoveConfig(AniConfiguration config) throws IllegalArgumentException {
        val type = getMoveType(config);
        if (type == CliConfiguration.MoveConfig.Type.FOLDER && config.getMoveToFolder().isBlank()) {
            throw new IllegalArgumentException("Move type is set to FOLDER but no folder is specified. (moveTypeUseFolder: true, moveToFolder: \"\")");
        }
        return CliConfiguration.MoveConfig.builder()
                .type(type)
                .folder(config.getMoveToFolder())
                .deleteEmptyDirs(config.isRecursivelyDeleteEmptyFolders())
                .duplicates(CliConfiguration.MoveConfig.HandlingConfig.builder()
                        .type(getDuplicatesType(config))
                        .folder(config.getDuplicatesFolder())
                        .build())
                .unknown(CliConfiguration.MoveConfig.HandlingConfig.builder()
                        .type(getUnknownType(config))
                        .folder(config.getUnknownFolder())
                        .build())
                .build();
    }

    private CliConfiguration.MoveConfig.HandlingConfig.Type getUnknownType(AniConfiguration config) {
        if (config.isMoveUnknownFiles()) {
            return CliConfiguration.MoveConfig.HandlingConfig.Type.MOVE;
        }
        return CliConfiguration.MoveConfig.HandlingConfig.Type.IGNORE;
    }

    private CliConfiguration.MoveConfig.HandlingConfig.Type getDuplicatesType(AniConfiguration config) {
        if (config.isMoveDuplicateFiles()) {
            return CliConfiguration.MoveConfig.HandlingConfig.Type.MOVE;
        }
        if (config.isDeleteDuplicateFiles()) {
            return CliConfiguration.MoveConfig.HandlingConfig.Type.DELETE;
        }
        return CliConfiguration.MoveConfig.HandlingConfig.Type.IGNORE;
    }

    private CliConfiguration.MoveConfig.Type getMoveType(AniConfiguration config) {
        if (!config.isEnableFileMove()) {
            return CliConfiguration.MoveConfig.Type.NONE;
        } else if (config.isMoveTypeUseFolder()) {
            return CliConfiguration.MoveConfig.Type.FOLDER;
        } else {
            return CliConfiguration.MoveConfig.Type.TAGSYSTEM;
        }
    }

    private CliConfiguration.RenameConfig.Type getRenameType(AniConfiguration config) {
        if (!config.isEnableFileRenaming()) {
            return CliConfiguration.RenameConfig.Type.NONE;
        } else if (config.isRenameTypeAniDBFileName()) {
            return CliConfiguration.RenameConfig.Type.ANIDB;
        } else {
            return CliConfiguration.RenameConfig.Type.TAGSYSTEM;
        }
    }

    private CliConfiguration.MyListConfig.StorageType convertStorageType(AniConfiguration.StorageType storageType) {
        return switch (storageType) {
            case UNKNOWN, UNKOWN -> CliConfiguration.MyListConfig.StorageType.UNKNOWN;
            case INTERNAL -> CliConfiguration.MyListConfig.StorageType.INTERNAL;
            case EXTERNAL -> CliConfiguration.MyListConfig.StorageType.EXTERNAL;
            case DELETED -> CliConfiguration.MyListConfig.StorageType.DELETED;
            case REMOTE -> CliConfiguration.MyListConfig.StorageType.REMOTE;
        };
    }
}
