package aniAdd.startup.commands;

import picocli.CommandLine;

public interface IValidation {
    void validate(CommandLine.Model.CommandSpec spec);
}
