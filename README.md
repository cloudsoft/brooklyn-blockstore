brooklyn-blockstore
===================

This provides an abstract for cloud blockstores (i.e. volumes), providing a common API for EC2, 
OpenStack, etc.

It provides operations for creating, attaching, mounting, unmounting, detaching and deleting 
volumes. It also includes the required filesystem operations for this.

To build the project, run:

    mvn clean install


## Checkout Brooklyn

This project builds on the open-source project Apache Brooklyn (see 
http://brooklyn.apache.org and https://github.com/apache/brooklyn).


## Example

First add the required jars to your Apache Brooklyn release (see "Future Work" for discussion 
of OSGi): 

    BROOKLYN_HOME=~/repos/apache/brooklyn/brooklyn-dist/dist/target/brooklyn-dist/brooklyn/
    BROOKLYN_BLOCKSTORE_REPO=~/repos/cloudsoft/brooklyn-blockstore
    BROOKLYN_BLOCKSTORE_VERSION=0.6.0-20170418.1354
    
    cp ${BROOKLYN_BLOCKSTORE_REPO}/blockstore/target/brooklyn-blockstore-${BROOKLYN_BLOCKSTORE_VERSION}.jar ${BROOKLYN_HOME}/lib/dropins/

And launch Brooklyn:

    ${BROOKLYN_HOME}/bin/brooklyn launch

Then deploy an app. The example below creates a VM with a new volume:

    location: aws-ec2:us-east-1
    services:
    - type: org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess
      brooklyn.config:
        launch.command: |
          sudo chown -R myname /mount/brooklyn/h/
          echo abc > /mount/brooklyn/h/myfile.txt
        checkRunning.command: echo checked
        provisioning.properties:
          user: myname
          customizers:
          - $brooklyn:object:
              type: brooklyn.location.blockstore.ec2.Ec2NewVolumeCustomizer
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
                    filesystemType: ext3

This second example creates a VM that binds to an existing volume:

    location:
      aws-ec2:us-east-1e
    services:
    - type: org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess
      brooklyn.config:
        launch.command: cat /mount/brooklyn/h/myfile.txt
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
                  filesystemType: ext3


## Future Work

This module should be built as an OSGi bundle, so that it can more easily be added to Brooklyn.

The API is under review, particularly to make it easier to use with YAML blueprints.
All feedback is extremely welcome!

The abstraction provides a common Java interface, and a cloud-agnostic way of instantiating 
an appropriate customizer: see `VolumeCustomizers`. However, the subtle differences between
clouds (e.g. the appropriate deviceSuffix, etc) makes it hard to write truly cloud-agnostic
code. This is a topic of continuing investigation and work. 


================

&copy; 2013-2016 Cloudsoft Corporation Limited. All rights reserved.

Use of this software is subject to the Cloudsoft EULA, provided in LICENSE.md and at 

http://www.cloudsoftcorp.com/cloudsoft-developer-license

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
