{
  description = "Trino Meilisearch connector development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          packages = [
            pkgs.curl
            pkgs.coreutils
            pkgs.docker-client
            pkgs.docker-compose
            pkgs.jq
            pkgs.jdk25
            pkgs.maven
            pkgs.nodejs
            pkgs.pnpm
            pkgs.trino-cli
          ];
        };
      });
    };
}
