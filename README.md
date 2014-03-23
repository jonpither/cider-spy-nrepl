# cider-spy-nrepl

## Middleware

Required Middleware to support [cider-spy](https://github.com/jonpither/cider-spy).

## Hub

Communicate and track fellow REPL hackers by running a hub:

`lein run`

## TODO

Handle server going down - clients become orphaned and nrepl needs bouncing.
Don't do a read-string on incoming code - reading could be dangerous! (maybe edn?)
There's some barfing in the read-command, related to the above, some work needed!

# License

Copyright © 2014 Jon Pither

Distributed under the GNU General Public License, version 3
