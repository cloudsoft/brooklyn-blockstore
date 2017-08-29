brooklyn-blockstore
===================

This provides an abstract for cloud blockstores (i.e. volumes), providing a common API for EC2, 
OpenStack, etc.
See [supported clouds](#supported-clouds) bellow.

It provides operations for creating, attaching, mounting, unmounting, detaching and deleting 
volumes. It also includes the required filesystem operations for this.

## Checkout Brooklyn

This project builds on the open-source project Apache Brooklyn (see
http://brooklyn.apache.org and https://github.com/apache/brooklyn).


## To build the project, run:

    git clone git@github.com:cloudsoft/brooklyn-blockstore.git
    mvn clean install


## Example Usage with Apache Brooklyn

_Note, Starting in OSGi.
To start the blockstore bundle you have to either load github.com/cloudsoft/jclouds-vcloud-director bundle
or make the "Import-Package" `org.jclouds.vcloud.director.v1_5.*` optional._
Assuming you build project and you know want to use it in Apache Brooklyn blueprints.

Create a catalog item and put location configuration for extending hard drives.
The example below creates a VM with a new volume:

    brooklyn.catalog:
      id: my-app.io.cloudsoft.brooklyn-blockstore.new-volume-customizer
      version: 0.6.0-20170829.1559 # BROOKLYN_BLOCKSTORE_VERSION
      brooklyn.libraries:
      - file://~/.m2/repository/io/brooklyn/blockstore/brooklyn-blockstore/0.6.0-20170829.1559/brooklyn-blockstore-0.6.0-20170829.1559.jar # BROOKLYN_BLOCKSTORE_VERSION
      item:
        services:
        - type: org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess
          brooklyn.config:
            launch.command: |
              sudo chown -R myname /mount/brooklyn/h/
              echo abc > /mount/brooklyn/h/myfile.txt
            checkRunning.command: echo checked
            provisioning.properties:
              customizers:
              - $brooklyn:object:
                  type: brooklyn.location.blockstore.NewVolumeCustomizer
                  object.fields:
                    volumes:
                    - blockDevice:
                        sizeInGb: 1
                        deviceSuffix: 'h'
                        deleteOnTermination: false
                        tags:
                          brooklyn: br-example-aled-1
                      filesystem:
                        mountPoint: /mount/brooklyn/h
                        filesystemType: ext4

This second example creates a VM that binds to an existing volume:

    brooklyn.catalog:
      id: my-app.io.cloudsoft.brooklyn-blockstore.ec2-existing-volume-customizer
      version: 0.6.0-20170829.1559 # BROOKLYN_BLOCKSTORE_VERSION
      name: My App with Ec2ExistingVolumeCustomizer # Not tested recently.
      brooklyn.libraries:
      - file://~/.m2/repository/io/brooklyn/blockstore/brooklyn-blockstore/0.6.0-20170829.1559/brooklyn-blockstore-0.6.0-20170829.1559.jar # BROOKLYN_BLOCKSTORE_VERSION
      item:
        services:
        - type: org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess
          brooklyn.config:
            launch.command: |
              sudo chown -R myname /mount/brooklyn/h/
              echo abc > /mount/brooklyn/h/myfile.txt
            checkRunning.command: echo checked
            provisioning.properties:
              customizers:
              - $brooklyn:object:
                  type: brooklyn.location.blockstore.ec2.Ec2ExistingVolumeCustomizer
                  object.fields:
                    volumeId: vol-87450758
                    blockOptions:
                      deviceSuffix: 'h'
                      deleteOnTermination: false
                    filesystemOptions:
                      mountPoint: /mount/brooklyn/h
                      filesystemType: ext4

The third example illustrates how your app is able to add additional disk via an effector 
and possibly a policy which triggers the effector when app space is ful.

     - type: org.apache.brooklyn.entity.database.postgresql.PostgreSqlNode
       brooklyn.initializers:
       - type: brooklyn.location.blockstore.effectors.ExtraHddBodyEffector

Effector takes a single parameter which is a json representation of what you would write in yaml for the `NewVolumeCustomizer`.

    {
      "blockDevice": {
        "sizeInGb": 3,
        "deviceSuffix": 'h',
        "deleteOnTermination": false,
        "tags": {
          "brooklyn": "br-example-test-1"
        }
      },
      "filesystem": {
        "mountPoint": "/mount/brooklyn/h",
        "filesystemType": "ext4"
      }
    }


## Supported clouds

`brooklyn.location.blockstore.NewVolumeCustomizer` and `brooklyn.location.blockstore.effectors.ExtraHddBodyEffector` support:

1. AWS EC2
2. Azure ARM
3. Vcloud Director 1.5 - To use brooklyn-blockstore with vcloud-director
 you should manually install [jclouds-vcloud-director](https://github.com/cloudsoft/jclouds-vcloud-director).
4. Google Compute Engine - GoogleComputeEngineVolumeManagerLiveTest#testCreateAndAttachVolume is creating vm in wrong region.
5. Old Openstack v2 with Cinder v1 - Untested since JAN 2017


## Future Work

The API is under review, particularly to make it easier to use with YAML blueprints.
All feedback is extremely welcome!

The abstraction provides a common Java interface, and a cloud-agnostic way of adding a disk:
see `brooklyn.location.blockstore.NewVolumeCustomizer` and `brooklyn.location.blockstore.effectors.ExtraHddBodyEffector`.
However, the subtle differences between clouds (e.g. the appropriate deviceSuffix) makes your app non-cloud agnostic.
This is a topic for which we would love to hear from you feedback and continue investigate and work on.


================

&copy; 2013-2017 Cloudsoft Corporation Limited. All rights reserved.

Use of this software is subject to the Cloudsoft EULA, provided in LICENSE.md and at 

http://www.cloudsoftcorp.com/cloudsoft-developer-license

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
