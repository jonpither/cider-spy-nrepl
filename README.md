# cider-spy-nrepl

[![Build Status](https://travis-ci.org/jonpither/cider-spy-nrepl.svg?branch=master)](https://travis-ci.org/jonpither/cider-spy-nrepl)

## Middleware

Required Middleware to support [cider-spy](https://github.com/jonpither/cider-spy). Also runs standalone to act as a hub for cider-spy-nrepls to communicate with each other.

### Basic configuration

`{:cider-spy-hub-host "localhost" ;;10.0.0.199
 :cider-spy-hub-port 7771}`


    :profiles {:dev {:dependencies [[cider-spy/cider-spy-nrepl "0.1.0-SNAPSHOT"]]
                       :repl-options {:nrepl-middleware [cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy
                                                       cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub]}}}`
## Hub

Communicate and track fellow REPL hackers by running a hub:

`lein run`

## TODO

Need a ping/pong from client to test the round loop
Warn if changing alias but no connection

# License

Copyright © 2014 Jon Pither

Distributed under the GNU General Public License, version 3
