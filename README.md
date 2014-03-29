# cider-spy-nrepl

## Middleware

Required Middleware to support [cider-spy](https://github.com/jonpither/cider-spy).

## Hub

Communicate and track fellow REPL hackers by running a hub:

`lein run`

## TODO

Need a ping/pong to test the round loop
Handle server going down - clients become orphaned and nrepl needs bouncing.
Uses clojure.tools.analyzer to filter multiple forms in code

# License

Copyright © 2014 Jon Pither

Distributed under the GNU General Public License, version 3
