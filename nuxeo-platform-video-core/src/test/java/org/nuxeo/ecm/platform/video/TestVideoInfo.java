/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.platform.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
public class TestVideoInfo {

    String[] ffmpegOutput = {
            "Input #0, matroska,webm, from 'test.mkv':",
            "  Duration: 00:21:09.02, start: 0.000000, bitrate: 448 kb/s",
            "    Stream #0.0(eng): Video: h264 (High), yuv420p, 1280x720, PAR 1:1 DAR 16:9, 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc",
            "    Stream #0.1: Audio: ac3, 48000 Hz, 5.1, s16, 448 kb/s (default)" };

    @Test
    public void testFFmpegOutputParsing() {
        VideoInfo videoInfo = VideoInfo.fromFFmpegOutput(Arrays.asList(ffmpegOutput));
        assertNotNull(videoInfo);

        assertEquals("matroska", videoInfo.getFormat());
        assertEquals(21 * 60 + 9 + 2d / 100, videoInfo.getDuration(), 0.1);
        assertEquals(1280, videoInfo.getWidth());
        assertEquals(720, videoInfo.getHeight());
        assertEquals(23.98, videoInfo.getFrameRate(), 0.1);

        List<Stream> streams = videoInfo.getStreams();
        assertEquals(2, streams.size());
        Stream stream = streams.get(0);
        assertEquals(Stream.VIDEO_TYPE, stream.getType());
        assertEquals("h264 (High)", stream.getCodec());
        assertEquals(
                "Stream #0.0(eng): Video: h264 (High), yuv420p, 1280x720, PAR 1:1 DAR 16:9, 23.98 fps, 23.98 tbr, 1k tbn, 47.95 tbc",
                stream.getStreamInfo());

        stream = streams.get(1);
        assertEquals(Stream.AUDIO_TYPE, stream.getType());
        assertEquals("ac3", stream.getCodec());
        assertEquals(
                "Stream #0.1: Audio: ac3, 48000 Hz, 5.1, s16, 448 kb/s (default)",
                stream.getStreamInfo());
    }

}
